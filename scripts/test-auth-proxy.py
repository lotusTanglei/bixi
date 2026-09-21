#!/usr/bin/env python3
"""Exercise both shipped Nginx auth proxies over HTTP against an isolated fake upstream.

Requires Docker and a cached nginx:alpine image (override BIXI_TEST_NGINX_IMAGE).
Run: python3 scripts/test-auth-proxy.py
"""
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import threading
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Upstream(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(302)
        location = self.headers.get('X-Test-Location')
        if location is None:
            path = self.headers.get('X-Test-Internal-Path', '/token/login')
            location = 'http://' + self.headers['Host'] + path
        self.send_header('Location', location)
        self.send_header('Set-Cookie', 'JSESSIONID=test-session; Path=' + self.headers.get('X-Test-Cookie-Path', '/') + '; HttpOnly')
        self.send_header('X-Upstream-Path', self.path)
        self.send_header('X-Upstream-Host', self.headers['Host'])
        self.end_headers()

    def log_message(self, *args):
        pass


class AuthProxyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.backend = ThreadingHTTPServer(('0.0.0.0', 0), Upstream)
        cls.backend.daemon_threads = True
        threading.Thread(target=cls.backend.serve_forever, daemon=True).start()
        cls.addClassCleanup(cls.backend.server_close)
        cls.addClassCleanup(cls.backend.shutdown)
        cls.directory = tempfile.TemporaryDirectory(prefix='bixi-auth-proxy-test-')
        cls.addClassCleanup(cls.directory.cleanup)
        cls.ports = {}
        cls.upstream_authority = 'host.docker.internal:' + str(cls.backend.server_port)
        for mode, original in (('single', 'single'), ('cloud', 'gateway')):
            config = (ROOT / 'deploy/nginx' / (mode + '.conf')).read_text()
            config = config.replace('http://' + original + ':9999', 'http://' + cls.upstream_authority)
            path = Path(cls.directory.name) / (mode + '.conf')
            path.write_text(config)
            command = ['docker', 'run', '--pull', 'never', '--rm', '-d', '-p', '127.0.0.1::8080',
                       '-v', str(path) + ':/etc/nginx/conf.d/default.conf:ro']
            if platform.system() == 'Linux':
                command += ['--add-host=host.docker.internal:host-gateway']
            command.append(os.environ.get('BIXI_TEST_NGINX_IMAGE', 'nginx:alpine'))
            container = subprocess.check_output(command, text=True, timeout=30).strip()
            cls.addClassCleanup(subprocess.run, ['docker', 'rm', '-f', container],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30, check=False)
            address = subprocess.check_output(['docker', 'port', container, '8080/tcp'], text=True, timeout=10).strip()
            cls.ports[mode] = int(address.rsplit(':', 1)[1])
            for attempt in range(50):
                try:
                    connection = HTTPConnection('127.0.0.1', cls.ports[mode], timeout=1)
                    connection.request('GET', '/healthz')
                    response = connection.getresponse()
                    response.read()
                    connection.close()
                    if response.status == 200:
                        break
                except OSError:
                    pass
                time.sleep(.1)
            else:
                logs = subprocess.check_output(['docker', 'logs', container], text=True, stderr=subprocess.STDOUT)
                raise AssertionError(mode + ' Nginx did not become ready: ' + logs)

    def request(self, mode, prefix, headers=None):
        connection = HTTPConnection('127.0.0.1', self.ports[mode], timeout=10)
        try:
            connection.request('GET', prefix + '/oauth2/authorize', headers=headers or {})
            response = connection.getresponse()
            result = dict(response.getheaders())
            response.read()
            self.assertEqual(response.status, 302)
            return result
        finally:
            connection.close()

    def test_single_preserves_external_callback_authorities(self):
        for prefix in ('/api/auth', '/api/admin'):
            for authority in ('client.example', '127.0.0.1:' + str(self.ports['single'] + 1)):
                for scheme in ('http', 'https'):
                    with self.subTest(prefix=prefix, authority=authority, scheme=scheme):
                        callback = scheme + '://' + authority + '/admin/callback?code=test-code&state=test-state'
                        headers = self.request('single', prefix, {'X-Test-Location': callback})
                        self.assertEqual(headers['Location'], callback)

    def test_single_rewrites_only_current_or_upstream_authority(self):
        for prefix in ('/api/auth', '/api/admin'):
            for location in (None, '/admin/token/login?continue=test',
                             'http://' + self.upstream_authority + '/admin/token/login?continue=test'):
                with self.subTest(prefix=prefix, location=location):
                    request_headers = {'X-Test-Internal-Path': '/admin/token/login?continue=test'}
                    if location is not None:
                        request_headers['X-Test-Location'] = location
                    headers = self.request('single', prefix, request_headers)
                    self.assertEqual(headers['Location'], prefix + '/token/login?continue=test')
                    self.assertEqual(headers['X-Upstream-Path'], '/admin/oauth2/authorize')
                    self.assertEqual(headers['X-Upstream-Host'], '127.0.0.1:' + str(self.ports['single']))

    def test_cloud_rewrites_auth_redirects_with_prefix_and_no_internal_port(self):
        for path in ('/token/login', '/oauth2/authorize?continue=test', '/token/confirm_access?state=test'):
            for location in (None, path, 'http://' + self.upstream_authority + path):
                with self.subTest(path=path, location=location):
                    request_headers = {'X-Test-Internal-Path': path}
                    if location is not None:
                        request_headers['X-Test-Location'] = location
                    headers = self.request('cloud', '/api/auth', request_headers)
                    self.assertEqual(headers['Location'], '/api/auth' + path)
                    self.assertEqual(headers['X-Upstream-Path'], '/auth/oauth2/authorize')
                    self.assertEqual(headers['X-Upstream-Host'], '127.0.0.1:' + str(self.ports['cloud']))

    def test_cloud_preserves_external_callback_authorities(self):
        for authority in ('client.example', '127.0.0.1:' + str(self.ports['cloud'] + 1)):
            for path in ('/token/login', '/oauth2/authorize', '/admin/callback'):
                with self.subTest(authority=authority, path=path):
                    callback = 'http://' + authority + path + '?code=test-code'
                    headers = self.request('cloud', '/api/auth', {'X-Test-Location': callback})
                    self.assertEqual(headers['Location'], callback)

    def test_session_cookie_paths_and_relative_locations(self):
        for mode, prefix, internal_path, external_path in (
                ('single', '/api/auth', '/admin', '/api/'),
                ('single', '/api/admin', '/admin', '/api/'),
                ('cloud', '/api/auth', '/', '/api/auth/')):
            with self.subTest(mode=mode, prefix=prefix):
                headers = self.request(mode, prefix, {'X-Test-Cookie-Path': internal_path,
                                                      'X-Test-Location': 'login?error=bad%20password'})
                self.assertIn('Path=' + external_path + ';', headers['Set-Cookie'])
                self.assertEqual(headers['Location'], 'login?error=bad%20password')

    def test_cloud_business_proxy_keeps_its_existing_routing(self):
        headers = self.request('cloud', '/api/admin', {'X-Test-Location': '/admin/example'})
        self.assertEqual(headers['X-Upstream-Path'], '/admin/oauth2/authorize')
        self.assertEqual(headers['Location'], '/admin/example')



if __name__ == '__main__':
    unittest.main()
