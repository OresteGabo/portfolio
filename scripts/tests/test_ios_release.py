import importlib.util
import pathlib
import plistlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('release_check', ROOT / 'scripts/check-ios-release.py')
release_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_check)


class ReleasePackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.app = pathlib.Path(self.temp.name)
        self.version = self.app / 'Version.xcconfig'
        self.version.write_text('MARKETING_VERSION = 1.2.3\nCURRENT_PROJECT_VERSION = 42\n')
        self.info = {'CFBundleIdentifier': 'dev.orestegabo.portfolio.Portfolio',
                     'CFBundleShortVersionString': '1.2.3', 'CFBundleVersion': '42',
                     'GaboPushEnabled': 'NO', 'GaboPushEnvironment': 'sandbox'}
        self.write_info()
        (self.app / 'PrivacyInfo.xcprivacy').write_bytes((ROOT / 'app/iosApp/iosApp/PrivacyInfo.xcprivacy').read_bytes())

    def write_info(self):
        (self.app / 'Info.plist').write_bytes(plistlib.dumps(self.info, fmt=plistlib.FMT_BINARY))

    def test_packaged_release(self):
        release_check.check(self.app, self.version)

    def test_missing_packaged_manifest(self):
        (self.app / 'PrivacyInfo.xcprivacy').unlink()
        with self.assertRaises(FileNotFoundError):
            release_check.check(self.app, self.version)

    def test_incorrect_release_metadata_or_transport_rejected(self):
        for key, value in [('CFBundleVersion', '41'), ('CFBundleIdentifier', 'example.debug'),
                           ('GaboDevelopmentApiOrigin', 'http://localhost:8080'),
                           ('GaboPushEnabled', ''), ('GaboPushEnabled', 'YES'),
                           ('NSAppTransportSecurity', {'NSAllowsArbitraryLoads': True})]:
            with self.subTest(key=key):
                original = self.info.copy()
                self.info[key] = value
                self.write_info()
                with self.assertRaises(AssertionError):
                    release_check.check(self.app, self.version)
                self.info = original
                self.write_info()
