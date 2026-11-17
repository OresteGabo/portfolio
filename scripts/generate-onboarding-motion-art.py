#!/usr/bin/env python3
"""Export independently animated layers from the existing onboarding vector masters."""
import argparse
from onboarding_png import trim, write_bounds
from pathlib import Path
import importlib.util
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/shared/src/commonMain/composeResources/drawable'
CHECK = False
BOUNDS = {}


def source(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def export(name, children, viewbox, width, height):
    svg = ET.Element('svg', xmlns='http://www.w3.org/2000/svg', viewBox=viewbox,
                     width=str(width), height=str(height), fill='none')
    svg.extend(children)
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / 'layer.svg'
        path.write_bytes(ET.tostring(svg))
        output = Path(directory) / 'layer.png'
        subprocess.run(['rsvg-convert', '-w', str(width * 2), '-h', str(height * 2),
                        '-o', str(output), str(path)], check=True)
        destination = DEST / f'{name}.png'
        data, bounds = trim(output.read_bytes())
        BOUNDS[name] = bounds
        if CHECK:
            if not destination.exists() or destination.read_bytes() != data:
                raise SystemExit(f'Stale onboarding artwork: {destination.name}; regenerate branding.')
        elif not destination.exists() or destination.read_bytes() != data:
            destination.write_bytes(data)


def main():
    calendar = source('calendar_art', 'branding/struct-capital/calendar/scripts/generate.py')
    documents = source('document_art', 'branding/struct-capital/data-processing/scripts/generate.py')
    for theme in ('light', 'dark'):
        palette = calendar.PALETTES[theme]
        phone = list(ET.fromstring(calendar.phone(palette)))
        upper = next(i for i, node in enumerate(phone) if node.get('d', '').startswith('M526 279'))
        lower = next(i for i, node in enumerate(phone) if node.get('d', '').startswith('M569 367'))
        logo = next(i for i, node in enumerate(phone) if node.get('id') == 'phone-struct-capital-symbol')
        layers = {
            'calendar_base': [ET.fromstring(calendar.calendar(palette))],
            'calendar_phone': phone[:upper] + phone[logo:],
            'calendar_notice_upper': phone[upper:lower],
            'calendar_notice_lower': phone[lower:logo],
        }
        for name, children in layers.items():
            export(f'onboarding_{name}_{theme}', children, '0 0 760 560', 760, 560)
        core = ET.fromstring(documents.core(documents.THEMES[theme], path_surface=True))
        for node in list(core):
            if node.get('id') == 'struct-capital-engraving':
                core.remove(node)
        export(f'onboarding_processor_blank_{theme}', [core], '160 70 320 240', 480, 360)
        pages = list(ET.fromstring(documents.document(documents.THEMES[theme])))
        for index, page in enumerate(pages[:3]):
            export(f'onboarding_report_sheet_{index}_{theme}', [page], '0 15 240 285', 320, 380)
        body_lines = {'M56 %sh' % y for y in [115,127,139,151,163,175,187,199]}
        clean_front = [node for node in pages[3:] if not any(node.get('d', '').startswith(prefix) for prefix in body_lines)]
        export(f'onboarding_report_blank_{theme}', clean_front, '0 15 240 285', 320, 380)
    write_bounds('Motion', BOUNDS, check=CHECK)
    print('Verified onboarding motion layers.' if CHECK else 'Exported onboarding motion layers from the current vector geometry.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail on stale PNGs without changing them')
    CHECK = parser.parse_args().check
    main()
