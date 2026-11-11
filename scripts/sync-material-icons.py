#!/usr/bin/env python3
"""Vendor only used extended Material icons, unchanged, from the upstream sources JAR.

Usage: python3 scripts/sync-material-icons.py <material-icons-extended-1.7.3-sources.jar>
The small material-icons-core dependency continues to supply common icons/builders.
"""
import argparse
from pathlib import Path
import re
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/shared/src/commonMain/kotlin/androidx/compose/material/icons'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('sources_jar', type=Path)
    args = parser.parse_args()
    requested = set()
    for tree in [ROOT / 'app', ROOT / 'core']:
        for path in tree.rglob('*.kt'):
            if 'build' in path.parts or path.is_relative_to(DEST):
                continue
            text = path.read_text()
            for style, name in re.findall(r'Icons\.((?:AutoMirrored\.)?(?:Filled|Default|Outlined|Rounded|Sharp|TwoTone))\.(\w+)', text):
                requested.add(style.lower().replace('default', 'filled').replace('.', '/') + '/' + name + '.kt')
            for name in re.findall(r'^import androidx\.compose\.material\.icons\.([\w.]+)', text, re.M):
                parts = name.rstrip('.').split('.')
                if len(parts) >= 2 and parts[-1][0].isupper():
                    requested.add('/'.join(parts) + '.kt')
    prefix = 'commonMain/androidx/compose/material/icons/'
    with ZipFile(args.sources_jar) as archive:
        available = {p[len(prefix):] for p in archive.namelist() if p.startswith(prefix) and p.endswith('.kt')}
        selected = requested & available
        for name in sorted(selected):
            target = DEST / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(prefix + name))
        for path in DEST.rglob('*.kt'):
            if path.relative_to(DEST).as_posix() not in selected:
                path.unlink()
    print(f'Vendored {len(selected)} used extended icons; upstream Apache headers preserved.')


if __name__ == '__main__':
    main()
