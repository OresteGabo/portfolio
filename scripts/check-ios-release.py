"""Check the built device app, not merely the source plist. No store upload/signing."""
import pathlib
import plistlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def check(app, version_file=ROOT / 'Version.xcconfig'):
    version = dict(line.split('=', 1) for line in version_file.read_text().splitlines() if '=' in line)
    version = {key.strip(): value.strip() for key, value in version.items()}
    with (app / 'Info.plist').open('rb') as source:
        info = plistlib.load(source)
    assert info['CFBundleIdentifier'] == 'dev.orestegabo.portfolio.Portfolio', 'Unexpected bundle ID'
    assert info['CFBundleShortVersionString'] == version['MARKETING_VERSION'], 'Marketing version drift'
    assert info['CFBundleVersion'] == version['CURRENT_PROJECT_VERSION'], 'Build number drift'
    assert not info.get('GaboDevelopmentApiOrigin'), 'Development API override in release'
    assert not info.get('NSAppTransportSecurity', {}).get('NSAllowsArbitraryLoads'), 'Unrestricted ATS'
    assert info.get('GaboPushEnabled') in ('NO', 'YES'), 'Push capability flag missing or unresolved'
    if info['GaboPushEnabled'] == 'YES':
        assert info.get('GaboPushEnvironment') == 'production', 'Release push must use production environment'
    with (app / 'PrivacyInfo.xcprivacy').open('rb') as source:
        privacy = plistlib.load(source)
    reasons = {entry['NSPrivacyAccessedAPIType']: entry['NSPrivacyAccessedAPITypeReasons']
               for entry in privacy['NSPrivacyAccessedAPITypes']}
    assert 'CA92.1' in reasons.get('NSPrivacyAccessedAPICategoryUserDefaults', []), 'UserDefaults reason missing'
    assert '35F9.1' in reasons.get('NSPrivacyAccessedAPICategorySystemBootTime', []), 'Timer reason missing'
    assert 'C617.1' in reasons.get('NSPrivacyAccessedAPICategoryFileTimestamp', []), 'App-container file metadata reason missing'
    assert privacy['NSPrivacyTracking'] is False, 'Unexpected tracking declaration'
    assert privacy['NSPrivacyCollectedDataTypes'], 'Financial/account collection inventory missing'


if __name__ == '__main__':
    check(pathlib.Path(sys.argv[1]))
    print('iOS device Release package checks passed (distribution signing not verified).')
