#!/usr/bin/env python3
"""Refresh the app's onboarding PNGs from the canonical theme-specific 2x art.
Run after rebuilding branding/struct-capital/{calendar,data-processing}/scripts/generate.py.
"""
from pathlib import Path
import shutil
import struct

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/shared/src/commonMain/composeResources/drawable'
PACKS = ROOT / 'branding/struct-capital'
SOURCES = {
    'processor': ('data-processing', 'processor-core'),
    'reports': ('data-processing', 'document-stack'),
    'allocation': ('data-processing', 'allocation-chart'),
    'value': ('data-processing', 'value-chart'),
    'event': ('data-processing', 'event-calendar'),
    'calendar': ('calendar', 'calendar-notifications'),
}


def dimensions(data):
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'Expected a PNG file'
    return struct.unpack('>II', data[16:24])


def main():
    changed = []
    # Validate every mapping first, then copy, preserving the existing image geometry.
    copies = []
    for target, (pack, source) in SOURCES.items():
        for theme in ('dark', 'light'):
            src = PACKS / pack / 'png/2x' / f'{source}-{theme}.png'
            dest = DEST / f'onboarding_{target}_{theme}.png'
            data = src.read_bytes()
            if dest.exists():
                assert dimensions(data) == dimensions(dest.read_bytes()), f'Unexpected size change: {dest}'
            copies.append((src, dest, data))
    for src, dest, data in copies:
        if not dest.exists() or dest.read_bytes() != data:
            shutil.copy2(src, dest)
            changed.append(dest.name)
    print(f'Updated {len(changed)} onboarding images: '+', '.join(changed))
    assert all(dest.read_bytes() == data for _, dest, data in copies)
    print(f'All {len(copies)} onboarding PNGs match the canonical 2x assets.')


if __name__ == '__main__':
    main()
