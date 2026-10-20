#!/usr/bin/env python3
"""Create a source delivery archive from tracked, non-secret files, with a manifest."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

repo = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
options = parser.parse_args()
output = options.output.resolve()
if output.is_relative_to(repo) and '.local' not in output.relative_to(repo).parts:
    raise SystemExit('Place generated archives outside the checkout or under ignored .local/.')
tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=repo).decode().split('\0')
files = []
for name in tracked:
    if not name:
        continue
    path = repo / name
    if path.is_symlink():
        raise SystemExit('Refusing symlink in delivery: ' + name)
    if name != '.env.example' and (path.name == '.env' or path.name.startswith('.env.')):
        raise SystemExit('Refusing local credentials in delivery.')
    if path.is_file():
        files.append((name, path.read_bytes(), path.stat().st_mode))
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo).decode().strip()
manifest = {'commit': head, 'workingTreeChanges': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=repo)),
            'files': {name: hashlib.sha256(data).hexdigest() for name, data, mode in files}}
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, data, mode in files:
        info = zipfile.ZipInfo('library-seat-reservation-system/' + name)
        info.create_system = 3
        info.external_attr = mode << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, data)
    archive.writestr('library-seat-reservation-system/DELIVERY-MANIFEST.json', json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
print('Source delivery created with checksums; local credentials, build caches and Git metadata excluded.')
