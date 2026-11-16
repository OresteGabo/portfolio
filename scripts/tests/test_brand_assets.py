"""Guard the shared source of truth for native brand marks."""
import json
import shutil
import subprocess
import sys
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class BrandAssetsTests(unittest.TestCase):
    def setUp(self):
        self.master = json.loads((ROOT / 'branding/inzira/refined-initial/geometry.json').read_text())

    def paths(self, relative):
        return [node.attrib[ANDROID + 'pathData'] for node in ET.parse(ROOT / relative).iter('path')]

    def test_light_dark_and_native_consumers_share_master_geometry(self):
        expected = list(self.master['paths'].values())
        for relative in [
            'app/shared/src/commonMain/composeResources/drawable/struct_mark_light.xml',
            'app/shared/src/commonMain/composeResources/drawable/struct_mark_dark.xml',
            'app/androidApp/src/main/res/drawable/ic_widget_brand.xml',
            'app/androidApp/src/main/res/drawable/ic_launcher_foreground.xml',
            'app/androidApp/src/main/res/drawable/ic_launcher_monochrome.xml',
            'app/androidApp/src/main/res/drawable/ic_price_notification.xml',
        ]:
            with self.subTest(resource=relative):
                self.assertEqual(expected, self.paths(relative))

    def test_ios_widget_and_import_action_share_master_geometry(self):
        for relative in ['branding/inzira/inzira-widget-symbol.svg',
                         'app/iosApp/ShareImport/ImportActionIcon.svg']:
            paths = [node.attrib['d'] for node in ET.parse(ROOT / relative).iter('{http://www.w3.org/2000/svg}path')]
            self.assertEqual(list(self.master['paths'].values()), paths)

    def test_animation_and_report_use_current_contours(self):
        generated = (ROOT / 'app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InziraBrandGeometry.generated.kt').read_text()
        for path in self.master['paths'].values():
            self.assertIn(path, generated)
        self.assertIn(self.master['journey'], generated)
        self.assertNotIn('UPPER_FOLD', generated)

    @unittest.skipUnless(shutil.which('rsvg-convert'), 'Onboarding render verification requires librsvg')
    def test_bundled_onboarding_images_match_current_vector_sources(self):
        for script in ['generate-onboarding-motion-art.py', 'sync-onboarding-art.py']:
            with self.subTest(generator=script):
                result = subprocess.run([sys.executable, str(ROOT / 'scripts' / script), '--check'],
                                        cwd=ROOT, capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
