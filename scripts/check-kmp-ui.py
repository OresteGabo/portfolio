#!/usr/bin/env python3
"""Validate reviewed UI evidence inventory; this is not a coverage measurement."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path('app/androidApp/src/androidTest/assets/ui-goldens')
TESTS = Path('app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments')


def validate(root, require_goldens=True):
    errors = []
    coverage = json.loads((root / 'docs/quality/ui-coverage.json').read_text())
    for flow in coverage['flows']:
        source = root / TESTS / (flow['class'] + '.kt')
        text = source.read_text() if source.is_file() else ''
        if not re.search(r'@Test\s+fun\s+' + re.escape(flow['method']) + r'\s*\(', text):
            errors.append(f"{flow['flow']}: missing @Test {flow['class']}.{flow['method']}")
    manifest = json.loads((root / ASSETS / 'manifest.json').read_text())
    expected = {f"{p['id']}-{theme}.png": (p['width'], p['height'])
                for p in manifest['profiles'] for theme in ('light', 'dark')}
    entries = {item['file']: item for item in manifest['goldens']}
    if len(entries) != len(manifest['goldens']):
        errors.append('Duplicate golden entries')
    if require_goldens and set(entries) != set(expected):
        errors.append('Golden inventory must cover every profile in both themes')
    for name, item in entries.items():
        path = root / ASSETS / name
        if name not in expected or Path(name).name != name or not path.is_file():
            errors.append(f'Invalid/missing golden: {name}')
            continue
        data = path.read_bytes()
        if len(data) < 24 or data[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', data[16:24]) != expected[name]:
            errors.append(f'Golden dimensions differ: {name}')
        if hashlib.sha256(data).hexdigest() != item['sha256']:
            errors.append(f'Golden hash differs: {name}; review before updating manifest')
    return errors


def verify_results(root, results):
    """Require inventoried methods to execute successfully, not merely exist in sources."""
    coverage = json.loads((root / 'docs/quality/ui-coverage.json').read_text())
    cases = [case for p in results.rglob('*.xml') for case in ET.parse(p).iter('testcase')]
    errors = ['Device report contains failures/errors'] if any(
        any(c.find(tag) is not None for tag in ('failure', 'error')) for c in cases) else []
    for flow in coverage['flows']:
        matching = [c for c in cases if c.get('classname', '').split('.')[-1] == flow['class']
                    and c.get('name', '').split('[')[0] == flow['method']]
        if not matching or any(any(c.find(tag) is not None for tag in ('failure', 'error', 'skipped')) for c in matching):
            errors.append(f"Required UI evidence did not pass: {flow['class']}.{flow['method']}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results', type=Path, help='JUnit report directory from a completed unfiltered device run')
    args = parser.parse_args()
    errors = validate(ROOT)
    if args.results:
        errors += verify_results(ROOT, args.results)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print('UI inventory and golden integrity valid' + ('; required journeys passed' if args.results else '; device execution not checked'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
