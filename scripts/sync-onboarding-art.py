#!/usr/bin/env python3
"""Refresh onboarding PNGs from the canonical theme-specific SVG generators.
Reads the source generator directly; no full-pack exports are required.
"""
import argparse
from onboarding_png import trim, write_bounds
from pathlib import Path
import importlib.util
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/shared/src/commonMain/composeResources/drawable'
PACKS = ROOT / 'branding/struct-capital'
# Animated chip/calendar layers come from generate-onboarding-motion-art.py;
# output charts are native Canvas paths. Only the document source is still used.
SOURCES = {'reports': ('data-processing', 'document-stack')}


def main(check=False):
    changed = []
    bounds = {}
    # Render every mapping first, preserving the existing image geometry.
    copies = []
    for target, (pack, source) in SOURCES.items():
        for theme in ('dark', 'light'):
            dest = DEST / f'onboarding_{target}_{theme}.png'
            generator = PACKS / pack / 'scripts/generate.py'
            spec = importlib.util.spec_from_file_location('onboarding_source', generator)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            with tempfile.TemporaryDirectory() as directory:
                art = Path(directory) / 'source.svg'
                art.write_text(module.svg(source, theme))
                output = Path(directory) / 'source.png'
                _, width, height, _, _ = module.ASSETS[source]
                subprocess.run(['rsvg-convert', '-w', str(width * 2), '-h', str(height * 2),
                                str(art), '-o', str(output)], check=True)
                data, bounds[f'onboarding_{target}_{theme}'] = trim(output.read_bytes())
            copies.append((dest, data))
    write_bounds('Document', bounds, check=check)
    for dest, data in copies:
        if not dest.exists() or dest.read_bytes() != data:
            changed.append(dest.name)
            if not check:
                dest.write_bytes(data)
    if check and changed:
        raise SystemExit('Stale onboarding artwork: ' + ', '.join(changed) + '; regenerate branding.')
    if not check:
        print(f'Updated {len(changed)} onboarding images: '+', '.join(changed))
    assert all(dest.read_bytes() == data for dest, data in copies)
    print(f'All {len(copies)} onboarding PNGs match the canonical SVG artwork.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail on stale PNGs without changing them')
    main(check=parser.parse_args().check)
