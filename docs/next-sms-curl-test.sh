#!/usr/bin/env bash
set -euo pipefail

# Python only builds JSON and HMAC; curl sends the HTTP request.
python3 - "$@" <<'PY'
import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import sys
import time
from urllib.parse import urlsplit

if len(sys.argv) < 2:
    sys.exit('Usage: bash next-sms-curl-test.sh <send|getBalance|getReport|getUpstream|getDailyStats|getSignDailyStats|secureSend> [date|maxSize|encrypted-body.json]')
endpoint = sys.argv[1]
args = sys.argv[2:]
sp_id = os.environ['NEXT_SMS_SP_ID']
sp_key = os.environ['NEXT_SMS_SP_KEY']
base_url = os.environ.get('NEXT_SMS_BASE_URL', 'https://nsms-api.wxo2o.cn').rstrip('/')
url = urlsplit(base_url)
if not re.fullmatch(r'[A-Za-z0-9_-]+', sp_id) or not sp_key:
    sys.exit('Invalid account configuration')
if url.scheme not in ('http', 'https') or not url.hostname or url.username or url.password or url.query or url.fragment:
    sys.exit('Invalid API root URL')

if endpoint in ('send', 'secureSend'):
    if os.environ.get('NEXT_SMS_CONFIRM_SEND') != 'YES':
        sys.exit('Set NEXT_SMS_CONFIRM_SEND=YES only for an authorized send test')
    if endpoint == 'send':
        if args:
            sys.exit('send takes no arguments; configure NEXT_SMS_* environment variables')
        payload = {'content': os.environ['NEXT_SMS_CONTENT'], 'mobile': os.environ['NEXT_SMS_MOBILE'],
                   'msgid': os.environ['NEXT_SMS_MSG_ID']}
        for name, field in [('NEXT_SMS_EXT_CODE', 'extCode'), ('NEXT_SMS_SID', 'sId'), ('NEXT_SMS_SEND_TIME', 'sendTime')]:
            value = os.environ.get(name)
            if value:
                payload[field] = value
    else:
        if len(args) != 1:
            sys.exit('secureSend requires an encrypted-body.json file generated with the same spKey')
        with open(args[0], encoding='utf-8') as source:
            payload = json.load(source)
        if not isinstance(payload, dict) or set(payload) != {'content'} or not isinstance(payload['content'], str):
            sys.exit('Expected an encrypted JSON object containing only content')
        ciphertext = base64.b64decode(payload['content'], validate=True)
        if not ciphertext or len(ciphertext) % 16:
            sys.exit('Invalid AES ciphertext length')
elif endpoint == 'getBalance':
    if args:
        sys.exit('getBalance takes no arguments')
    payload = None
elif endpoint in ('getReport', 'getUpstream'):
    if len(args) > 1:
        sys.exit('Expected at most one maxSize argument')
    size = int(args[0]) if args else 500
    if not 10 <= size <= 1000:
        sys.exit('maxSize must be between 10 and 1000')
    payload = {'maxSize': size}
elif endpoint in ('getDailyStats', 'getSignDailyStats'):
    if len(args) != 1 or not re.fullmatch(r'\d{8}', args[0]):
        sys.exit('Expected one date argument in yyyyMMdd format')
    payload = {'date': args[0]}
else:
    sys.exit('Unknown endpoint')

body = b'' if payload is None else json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
timestamp = str(time.time_ns() // 1_000_000)
signature = base64.b64encode(hmac.new(sp_key.encode('utf-8'), body + timestamp.encode('ascii'), hashlib.sha256).digest()).decode('ascii')
command = ['curl', '--silent', '--show-error', '--connect-timeout', '5', '--max-time', '30',
           '--proto', '=http,https', '--request', 'POST', '--header', 'Content-Type: application/json; charset=utf-8',
           '--header', 'Authorization: HMAC-SHA256 ' + timestamp + ',' + signature,
           '--data-binary', '@-', '--write-out', '\n%{http_code}', base_url + '/sms/' + endpoint + '/' + sp_id]
response = subprocess.run(command, input=body, stdout=subprocess.PIPE, check=False)
if response.returncode:
    sys.exit('curl failed (exit %d); do not automatically repeat a send or queue pull' % response.returncode)
raw, http_status = response.stdout.rsplit(b'\n', 1)
if not 200 <= int(http_status) < 300:
    sys.exit('HTTP error: ' + http_status.decode('ascii'))
result = json.loads(raw)
print(json.dumps(result, ensure_ascii=False, indent=2))
if result['status'] != 0:
    sys.exit(1)
PY
