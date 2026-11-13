"""Sync native/vector brand colors from the shared Kotlin source; --check is read-only CI validation."""
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / 'app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/design'
SOURCE = DESIGN / 'StructColors.kt'
GENERATED_NOTE = '<!-- Generated colors: scripts/sync-design-colors.py; edit design/StructColors.kt. -->\n'


def palette(source):
    result = {}
    for group, names in [('Brand', ['olive', 'cream', 'canvas']), ('Fixed', ['white']),
                         ('Widget', ['background', 'outline', 'secondaryText', 'text']),
                         ('BondNotification', ['trackLight', 'trackDark', 'badgeLight', 'badgeDark'])]:
        section = re.search(r'object ' + group + r' \{(.*?)\n    \}', source, re.S).group(1)
        for name in names:
            argb = re.search(r'val ' + name + r' = Color\(0x([A-Fa-f0-9]{8})\)', section).group(1).upper()
            result[group + '.' + name] = '#' + (argb[2:] if argb.startswith('FF') else argb)
    return result


def outputs():
    colors = palette(SOURCE.read_text())
    rows = [('struct_brand_olive', 'Brand.olive'), ('struct_brand_cream', 'Brand.cream'),
            ('struct_brand_canvas', 'Brand.canvas'), ('struct_fixed_white', 'Fixed.white'),
            ('struct_widget_background', 'Widget.background'), ('struct_widget_outline', 'Widget.outline'),
            ('struct_widget_secondary_text', 'Widget.secondaryText'), ('struct_widget_text', 'Widget.text'),
            ('struct_bond_track_light', 'BondNotification.trackLight'), ('struct_bond_track_dark', 'BondNotification.trackDark'),
            ('struct_bond_badge_light', 'BondNotification.badgeLight'), ('struct_bond_badge_dark', 'BondNotification.badgeDark')]
    result = {ROOT / 'app/androidApp/src/main/res/values/struct_colors.xml':
              GENERATED_NOTE + '<resources>\n' + ''.join(f'    <color name="{name}">{colors[key]}</color>\n' for name, key in rows) + '</resources>\n'}
    for theme, key in [('light', 'Brand.olive'), ('dark', 'Brand.cream')]:
        path = ROOT / f'app/shared/src/commonMain/composeResources/drawable/struct_mark_{theme}.xml'
        text = path.read_text().removeprefix(GENERATED_NOTE)
        def paint(match):
            if match.group(2).upper() == '#00000000':
                return match.group(0)  # Transparent geometry is not a brand color.
            return f'android:{match.group(1)}="{colors[key]}"'
        result[path] = GENERATED_NOTE + re.sub(r'android:(fillColor|strokeColor)="(#[A-Fa-f0-9]{6,8})"', paint, text)
    return result


def audit():
    issues = []
    raw = re.compile(r'\bColor\(\s*(?:0x[0-9A-Fa-f]+|[0-9.]+f?\s*,)|\b(?:Color\.(?:White|Black|Gray|Red|Green|Blue|Yellow|Cyan|Magenta|LightGray|DarkGray|WHITE|BLACK|GRAY|RED|GREEN|BLUE|YELLOW|CYAN|MAGENTA)|UIColor\.whiteColor)\b|(?:UI)?Color\(\s*red\s*[:=]\s*[0-9]|\bColor\.(?:rgb|argb)\(\s*[0-9]')
    paths = []
    for source_set in ['commonMain', 'androidMain', 'iosMain']:
        paths.extend((ROOT / f'app/shared/src/{source_set}/kotlin').rglob('*.kt'))
    paths.extend((ROOT / 'app/androidApp/src/main/kotlin').rglob('*.kt'))
    paths.extend((ROOT / 'app/iosApp/iosApp').rglob('*.swift'))
    for path in paths:
        if 'design' in path.relative_to(ROOT).parts:
            continue
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if raw.search(line):
                issues.append(f'{path.relative_to(ROOT)}:{number}: hardcoded presentation color outside design package')
    generated_native = ROOT / 'app/androidApp/src/main/res/values/struct_colors.xml'
    for path in (ROOT / 'app/androidApp/src/main/res').rglob('*.xml'):
        if path != generated_native and re.search(r'#[A-Fa-f0-9]{6,8}\b', path.read_text()):
            issues.append(f'{path.relative_to(ROOT)}: use generated named color resources')
    # There must be one authored definition per exact ARGB value. Semantic aliases may reference it.
    definitions = {}
    for path in DESIGN.glob('*.kt'):
        for value in re.findall(r'Color\(0x([0-9A-Fa-f]{8})\)', path.read_text()):
            if value.upper() in definitions:
                issues.append(f'{path.relative_to(ROOT)}: duplicate ARGB value {value}; alias {definitions[value.upper()]}')
            definitions[value.upper()] = path.name
    return issues


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    errors = audit()
    for path, expected in outputs().items():
        if args.check:
            if not path.exists() or path.read_text() != expected:
                errors.append(f'{path.relative_to(ROOT)}: generated colors are stale; run python3 scripts/sync-design-colors.py')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected)
    if errors:
        raise SystemExit('\n'.join(errors))
    print('Design colors: source audit and generated resources are consistent.')


if __name__ == '__main__':
    main()
