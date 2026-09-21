#!/usr/bin/env python3
"""Exercise the deployed OAuth browser flow with a real cookie jar, without following external callbacks."""
import base64
from html.parser import HTMLParser
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import sys
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urljoin, urlsplit
from urllib.request import build_opener, HTTPCookieProcessor, HTTPRedirectHandler, Request


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Form(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.action = None
        self.values = {}
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'form':
            self.action = attrs.get('action')
        if tag == 'input' and attrs.get('name'):
            if attrs.get('type') in ('hidden', 'checkbox'):
                self.values[attrs['name']] = attrs.get('value', '')


root = Path(__file__).resolve().parents[1]
env = {}
for line in Path(os.environ.get('BIXI_ENV_FILE', root / '.env')).read_text().splitlines():
    if line.strip() and not line.lstrip().startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        env[key.strip()] = value.strip().strip('\"\'')
env.update(os.environ)
mode = env.get('BIXI_MODE', 'cloud')
origin = env.get('BIXI_ORIGIN', 'http://localhost:' + env.get('BIXI_HTTP_PORT', '8080')).rstrip('/')
api = env.get('BIXI_API_BASE_URL', origin + '/api').rstrip('/')
auth = api + '/auth'
callback = env.get('BIXI_AUTH_TEST_REDIRECT_URI', 'https://invalid.invalid/oauth/callback')
jar = http.cookiejar.CookieJar()
browser = build_opener(HTTPCookieProcessor(jar), NoRedirect())
checked = []
issued_token = None
failure = None
test_username = env.get('BIXI_AUTH_TEST_USERNAME')
test_password = env.get('BIXI_AUTH_TEST_PASSWORD')


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def request(url, data=None, method=None, headers=None):
    # Credentials, cookies, authorization codes and response bodies are never printed.
    check(urlsplit(url).netloc == urlsplit(api).netloc, 'Refusing to request an external callback')
    req = Request(url, data=urlencode(data).encode() if data is not None else None,
                  method=method, headers={'Accept': 'text/html', **(headers or {})})
    try:
        response = browser.open(req, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read().decode()


def login_page():
    status, _, html = request(auth + '/token/login')
    form = Form(html)
    check(status == 200 and form.values.get('_csrf'), 'Login page must render a CSRF token')
    check(urljoin(auth + '/token/login', form.action) == auth + '/token/form',
          'Login form action must preserve the external auth prefix')
    return form


def location_at(url, response):
    status, headers, _ = response
    check(status in (302, 303) and headers.get('Location'), 'Expected a browser redirect')
    return urljoin(url, headers['Location'])


try:
    check(test_username and test_password,
          'Use security-acceptance.mjs to provide a newly created, disposable browser test user')
    page = login_page()
    for csrf in (None, 'invalid'):
        data = {'username': test_username, 'password': test_password}
        if csrf is not None:
            data['_csrf'] = csrf
        status, _, _ = request(auth + '/token/form', data)
        check(status == 403, 'Missing or invalid login CSRF must return 403')
    checked.append('missing and invalid form CSRF rejected')

    wrong_password = 'incorrect-' + secrets.token_urlsafe(24)
    for grant in (None, 'mobile', 'client_credentials'):
        page = login_page()
        data = {**page.values, 'username': test_username, 'password': wrong_password}
        data.pop('grant_type', None)
        if grant:
            data['grant_type'] = grant
        target = location_at(auth + '/token/form', request(auth + '/token/form', data))
        check(urlsplit(target).path == urlsplit(auth + '/token/login').path and 'error=' in target,
              'Wrong password must return to the externally routed login error page')
    checked.append('form password cannot be bypassed with a forged grant type')

    state = secrets.token_urlsafe(18)
    authorize = auth + '/oauth2/authorize?' + urlencode({
        'response_type': 'code', 'client_id': 'bixi', 'redirect_uri': callback,
        'scope': 'server', 'state': state})
    target = location_at(authorize, request(authorize))
    check(urlsplit(target).netloc == urlsplit(api).netloc and
          urlsplit(target).path == urlsplit(auth + '/token/login').path,
          'Authorization login redirect must retain public host, port and prefix')
    page = login_page()
    result = request(auth + '/token/form', {**page.values, 'username': test_username,
                                          'password': test_password})
    target = location_at(auth + '/token/form', result)
    consent_submitted = False
    for _ in range(5):
        if urlsplit(target).netloc == urlsplit(callback).netloc:
            break
        check(urlsplit(target).netloc == urlsplit(api).netloc and
              urlsplit(target).path.startswith(urlsplit(auth).path + '/'),
              'Saved authorization and consent redirects must retain the public auth prefix')
        current = target
        result = request(current)
        if result[0] == 200:
            form = Form(result[2])
            check(form.values.get('client_id') == 'bixi' and form.values.get('state'),
                  'Expected the real consent form')
            target = urljoin(current, form.action)
            check(target == auth + '/oauth2/authorize', 'Consent form must submit through the auth prefix')
            result = request(target, form.values)
            consent_submitted = True
            current = target
        target = location_at(current, result)
    response_values = parse_qs(urlsplit(target).query)
    check(consent_submitted, 'This new user must see and submit the real consent form')
    check(target.startswith(callback + '?') and response_values.get('state') == [state]
          and response_values.get('code'), 'OAuth flow must return an authorization code with matching state')
    checked.append('authorization, session login, saved request and consent complete through proxy')

    basic = base64.b64encode(('bixi:' + env['OAUTH_PASSWORD_CLIENT_SECRET']).encode()).decode()
    status, _, body = request(auth + '/oauth2/token', {
        'grant_type': 'authorization_code', 'code': response_values['code'][0], 'redirect_uri': callback},
        headers={'Authorization': 'Basic ' + basic})
    token_response = json.loads(body)
    check(status == 200 and token_response.get('access_token'), 'Authorization code exchange must succeed')
    issued_token = token_response['access_token']
    status, _, _ = request(api + '/admin/user/info', headers={'Accept': 'application/json'})
    check(status in (401, 424), 'Browser session alone must not authorize a business API')
    status, _, body = request(api + '/admin/user/info', headers={
        'Authorization': 'Bearer ' + issued_token, 'Accept': 'application/json'})
    check(status == 200 and json.loads(body).get('code') == 0, 'Bearer token must authorize the same API')
    checked.append('business APIs require Bearer even with an authenticated browser session')

    request(auth + '/logout')  # May render the standard confirmation page; must not clear authentication.
    target = location_at(authorize, request(authorize))
    check(urlsplit(target).path != urlsplit(auth + '/token/login').path, 'GET logout must not end the session')
    status, _, _ = request(auth + '/logout', {})
    check(status == 403, 'POST logout without CSRF must be rejected')
    page = login_page()
    target = location_at(auth + '/logout', request(auth + '/logout', {'_csrf': page.values['_csrf']}))
    check(urlsplit(target).path == urlsplit(auth + '/token/login').path, 'Logout redirect must retain auth prefix')
    target = location_at(authorize, request(authorize))
    check(urlsplit(target).path == urlsplit(auth + '/token/login').path, 'CSRF-protected logout must end session')
    checked.append('logout changes session only with a valid CSRF-protected POST')
except Exception as error:
    failure = str(error) if isinstance(error, AssertionError) else type(error).__name__
finally:
    if issued_token:
        try:
            status, _, body = request(auth + '/token/logout', method='DELETE',
                                      headers={'Authorization': 'Bearer ' + issued_token, 'Accept': 'application/json'})
            check(status == 200 and json.loads(body).get('code') == 0, 'Browser test token cleanup failed')
        except Exception:
            failure = failure or 'Browser test token cleanup failed'

print(json.dumps({'mode': mode, 'status': 'failed' if failure else 'passed',
                  'checked': checked, **({'error': failure} if failure else {})}, indent=2))
sys.exit(1 if failure else 0)
