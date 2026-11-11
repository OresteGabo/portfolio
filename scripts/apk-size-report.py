#!/usr/bin/env python3
"""Report compressed APK payloads and guard against accidental asset/tooling bloat."""
import argparse
import json
from collections import Counter
from pathlib import Path
from zipfile import ZipFile


def category(name):
    if name.endswith('.dex'):
        return 'DEX code'
    if name.startswith('assets/engine/bond-import/tesseract/'):
        return 'OCR cores'
    if 'eng.traineddata' in name:
        return 'OCR English model'
    if '/drawable/kigali_' in name:
        return 'Photography'
    if name.startswith('assets/engine/'):
        return 'PDF import runtime'
    if name.startswith('assets/composeResources/'):
        return 'Shared UI and data assets'
    if name.startswith('lib/'):
        return 'Native libraries'
    return 'Android resources and metadata'


def inspect(path, verify=False):
    with ZipFile(path) as archive:
        entries = archive.infolist()
        groups = Counter()
        for entry in entries:
            groups[category(entry.filename)] += entry.compress_size
        if verify:
            names = set(archive.namelist())
            prefix = 'assets/engine/bond-import/tesseract/'
            expected = {prefix + 'tesseract-core' + variant + '-lstm.wasm.js'
                        for variant in ['', '-simd', '-relaxedsimd']}
            assert {n for n in names if n.startswith(prefix)} == expected, 'Unexpected OCR core payload'
            assert 'assets/engine/bond-import/eng.traineddata' in names, 'Offline English OCR model missing'
            photos = {n.rsplit('/', 1)[-1] for n in names if '/drawable/kigali_' in n}
            assert photos == {'kigali_hero.jpg', 'kigali_assets_36653828.jpg'}, 'Unused photos packaged or active photos missing'
            for name in names:
                if name.endswith('.dex'):
                    assert b'androidx/compose/ui/tooling/ComposeViewAdapter' not in archive.read(name), 'Developer UI tooling packaged in release'
        return {
            'apk': str(path), 'bytes': path.stat().st_size,
            'compressed_groups': dict(groups.most_common()),
            'largest_entries': [{'path': e.filename, 'compressed': e.compress_size, 'uncompressed': e.file_size}
                                for e in sorted(entries, key=lambda e: e.compress_size, reverse=True)[:20]],
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', nargs='+', type=Path)
    parser.add_argument('--verify-release', action='store_true')
    parser.add_argument('--max-mib', type=float)
    parser.add_argument('--json', type=Path)
    args = parser.parse_args()
    reports = [inspect(path, args.verify_release) for path in args.apks]
    for report in reports:
        size = report['bytes'] / 1048576
        print(f"{report['apk']}: {size:.2f} MiB")
        for name, count in report['compressed_groups'].items():
            print(f'  {name}: {count / 1048576:.2f} MiB')
        if args.max_mib is not None:
            assert size <= args.max_mib, f'APK exceeds {args.max_mib} MiB budget'
    if args.json:
        args.json.write_text(json.dumps(reports, indent=2) + '\n')


if __name__ == '__main__':
    main()
