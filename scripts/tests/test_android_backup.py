"""Release policy: private metadata and device-bound vaults must not migrate."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'
DOMAINS = {'root', 'file', 'database', 'sharedpref', 'external',
           'device_root', 'device_file', 'device_database', 'device_sharedpref'}


class AndroidBackupTests(unittest.TestCase):
    def test_manifest_disables_legacy_backup_and_selects_transfer_policy(self):
        app = ET.parse(ROOT / 'app/androidApp/src/main/AndroidManifest.xml').find('application')
        self.assertEqual(app.get(ANDROID + 'allowBackup'), 'false')
        self.assertEqual(app.get(ANDROID + 'fullBackupContent'), 'false')
        self.assertEqual(app.get(ANDROID + 'dataExtractionRules'), '@xml/data_extraction_rules')

    def test_cloud_and_device_transfer_exclude_all_private_storage_domains(self):
        rules = ET.parse(ROOT / 'app/androidApp/src/main/res/xml/data_extraction_rules.xml')
        self.assertEqual(rules.getroot().tag, 'data-extraction-rules')
        for transport in ('cloud-backup', 'device-transfer'):
            with self.subTest(transport=transport):
                policy = rules.find(transport)
                self.assertIsNotNone(policy)
                self.assertEqual(policy.findall('include'), [])
                self.assertEqual({node.get('domain') for node in policy.findall('exclude')
                                  if node.get('path') == '.'}, DOMAINS)
