#!/usr/bin/env python3
"""Check Nginx's session-cookie mapping using the standard HTTP cookie-jar path rules."""
import http.cookiejar
from pathlib import Path
import re
import unittest
from urllib.request import Request


class SingleAuthCookieTest(unittest.TestCase):
    def test_auth_aliases_return_session_cookie_on_login_submission(self):
        config = (Path(__file__).resolve().parents[1] / 'deploy/nginx/single.conf').read_text()
        for location in ('/api/auth/', '/api/'):
            with self.subTest(location=location):
                block = re.search(r'location\s+' + re.escape(location) + r'\s*\{([^}]+)\}', config).group(1)
                mapping = re.search(r'proxy_cookie_path\s+/admin\s+([^;]+);', block)
                # Without this rewrite a browser cannot send Path=/admin cookies to /api/*.
                cookie_path = mapping.group(1).strip() if mapping else '/admin'
                jar = http.cookiejar.CookieJar()
                jar.set_cookie(http.cookiejar.Cookie(
                    version=0, name='JSESSIONID', value='test-session', port=None, port_specified=False,
                    domain='example.test', domain_specified=False, domain_initial_dot=False,
                    path=cookie_path, path_specified=True, secure=True, expires=None, discard=True,
                    comment=None, comment_url=None, rest={'HttpOnly': None}))
                for path in ('/api/auth/token/form', '/api/admin/token/form', '/api/auth/oauth2/authorize'):
                    request = Request('https://example.test' + path)
                    jar.add_cookie_header(request)
                    self.assertEqual(request.get_header('Cookie'), 'JSESSIONID=test-session', path)
                request = Request('https://example.test/unrelated')
                jar.add_cookie_header(request)
                self.assertIsNone(request.get_header('Cookie'))


if __name__ == '__main__':
    unittest.main()
