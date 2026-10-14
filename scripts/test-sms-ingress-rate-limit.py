#!/usr/bin/env python3
"""Exercise the public SMS rate limit in each release Nginx configuration."""

from http.client import HTTPSConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import shutil
import socket
import ssl
import subprocess
import tempfile
import threading
import time


ROOT = Path(__file__).resolve().parents[1]
CONFIGS = (
    ROOT / 'deploy/nginx/https.conf.template',
    ROOT / 'deploy/native/nginx/cloud.conf',
    ROOT / 'deploy/native/nginx/single.conf',
)


class Upstream(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(204)
        self.send_header('X-Upstream-Path', self.path)
        self.end_headers()

    def log_message(self, *args):
        pass


def free_port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


def request(port, path, headers=None):
    connection = HTTPSConnection('127.0.0.1', port, context=ssl._create_unverified_context(), timeout=3)
    try:
        connection.request('GET', path, headers=headers or {})
        response = connection.getresponse()
        response.read()
        return response.status, response.getheader('X-Upstream-Path')
    finally:
        connection.close()


def check(config_path, directory, certificate, private_key):
    port = free_port()
    http_port = free_port()
    upstream = ThreadingHTTPServer(('127.0.0.1', 0), Upstream)
    upstream.daemon_threads = True
    threading.Thread(target=upstream.serve_forever, daemon=True).start()
    config = config_path.read_text()
    config = config.replace('listen 8443 ssl;', f'listen 127.0.0.1:{port} ssl;')
    config = config.replace('listen 443 ssl http2;', f'listen 127.0.0.1:{port} ssl;')
    config = config.replace('listen 80;', f'listen 127.0.0.1:{http_port};')
    config = config.replace('/etc/nginx/tls/fullchain.pem', str(certificate))
    config = config.replace('/etc/nginx/tls/privkey.pem', str(private_key))
    config = config.replace('/etc/bixi/tls/fullchain.pem', str(certificate))
    config = config.replace('/etc/bixi/tls/privkey.pem', str(private_key))
    config = config.replace('${BIXI_FRONTEND_UPSTREAM}', f'127.0.0.1:{upstream.server_port}')
    config = config.replace('127.0.0.1:9999', f'127.0.0.1:{upstream.server_port}')
    site = directory / 'site.conf'
    site.write_text(config)
    main = directory / 'nginx.conf'
    main.write_text(f'pid {directory / "nginx.pid"};\nevents {{}}\nhttp {{ include {site}; }}\n')
    command = ['nginx', '-c', str(main), '-p', str(directory), '-g', 'daemon off;']
    subprocess.run(command[:-2] + ['-t'], check=True, capture_output=True, text=True)
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        for _ in range(30):
            if process.poll() is not None:
                raise AssertionError(f'{config_path}: Nginx exited: {process.stderr.read()}')
            try:
                request(port, '/healthz')
                break
            except OSError:
                time.sleep(.1)
        else:
            raise AssertionError(f'{config_path}: Nginx did not start')

        first, forwarded = request(port, '/api/admin/mobile/13800138000?source=test')
        expected = ('/api' if config_path.name == 'https.conf.template' else '') + '/admin/mobile/13800138000?source=test'
        if first != 204 or forwarded != expected:
            raise AssertionError(f'{config_path}: SMS route did not reach expected upstream path ({first}, {forwarded})')
        for i in range(6):
            status, _ = request(port, '/api/admin/mobile/13800138000',
                                {'X-Forwarded-For': f'203.0.113.{i + 1}'})
        if status != 429:
            raise AssertionError(f'{config_path}: forged X-Forwarded-For bypassed SMS limit ({status})')
        for path in ('/api/admin/%6dobile/13800138000', '/api/admin//mobile/13800138000'):
            status, _ = request(port, path)
            if status != 429:
                raise AssertionError(f'{config_path}: normalized SMS path bypassed limit: {path} ({status})')
        if request(port, '/healthz')[0] == 429:
            raise AssertionError(f'{config_path}: non-SMS request was throttled')
    finally:
        process.terminate()
        try:
            process.communicate(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate()
        upstream.shutdown()
        upstream.server_close()


if __name__ == '__main__':
    if not shutil.which('nginx') or not shutil.which('openssl'):
        raise SystemExit('nginx and openssl are required for the SMS ingress test')
    with tempfile.TemporaryDirectory(prefix='bixi-sms-ingress-') as name:
        directory = Path(name)
        certificate = directory / 'cert.pem'
        private_key = directory / 'key.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=localhost', '-keyout', str(private_key), '-out', str(certificate)],
                       check=True, capture_output=True)
        for index, path in enumerate(CONFIGS):
            site_directory = directory / str(index)
            site_directory.mkdir()
            check(path, site_directory, certificate, private_key)
    print('Public SMS ingress rate limits passed for HTTPS, native cloud and native single.')
