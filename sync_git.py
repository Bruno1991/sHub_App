import subprocess
import os
import re

token = [re.search(r'FULL_ACESS_TOKEN=(.*)', l).group(1).strip() for l in open('../.env', encoding='utf-8').readlines() if l.startswith('FULL_ACESS_TOKEN=')][0]
repo_url = f'https://{token}@github.com/Bruno1991/sHub_App.git'

subprocess.run(['git', 'config', 'http.postBuffer', '524288000'])
subprocess.run(['git', 'reset', 'remote_main'])
subprocess.run(['git', 'add', '-A'])

st = subprocess.run(['git', 'status', '--short'], capture_output=True, text=True).stdout.strip()
print('Staged files count:', len(st.splitlines()))

for line in st.splitlines():
    fname = line[3:].strip().strip('"')
    if os.path.exists(fname):
        sz = os.path.getsize(fname)
        if sz > 20000000:
            print('Large staged file:', fname, round(sz/1024/1024, 2), 'MB')

subprocess.run(['git', 'commit', '-m', 'feat(app): synchronize sHub_App architecture, modules and assets'])
res = subprocess.run(['git', 'push', repo_url, 'main'], capture_output=True, text=True)
print('Push result:', 'OK' if res.returncode == 0 else res.stderr)
