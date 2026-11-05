import contextlib
import importlib.util
import io
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).resolve().parents[1] / 'sync-design-colors.py'
spec = importlib.util.spec_from_file_location('design_colors', SCRIPT)
colors = importlib.util.module_from_spec(spec)
spec.loader.exec_module(colors)


class DesignColorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for relative in [str(colors.SOURCE.relative_to(colors.ROOT)),
                         'app/shared/src/commonMain/composeResources/drawable/struct_mark_light.xml',
                         'app/shared/src/commonMain/composeResources/drawable/struct_mark_dark.xml']:
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(colors.ROOT / relative, target)
        self.source = self.root / colors.SOURCE.relative_to(colors.ROOT)
        self.design = self.source.parent
        for key, value in [('ROOT', self.root), ('SOURCE', self.source), ('DESIGN', self.design)]:
            patcher = patch.object(colors, key, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def test_one_brand_edit_reaches_native_resources_and_both_logo_paths(self):
        self.source.write_text(self.source.read_text().replace('0xFF6D5E0F', '0xFF123456'))
        outputs = colors.outputs()
        native = outputs[self.root / 'app/androidApp/src/main/res/values/struct_colors.xml']
        light = outputs[self.root / 'app/shared/src/commonMain/composeResources/drawable/struct_mark_light.xml']
        dark = outputs[self.root / 'app/shared/src/commonMain/composeResources/drawable/struct_mark_dark.xml']
        self.assertIn('>#123456</color>', native)
        self.assertIn('android:strokeColor="#123456"', light)
        self.assertIn('android:fillColor="#123456"', light)
        self.assertNotIn('#6D5E0F', light)
        self.assertNotIn('#123456', dark)
        self.assertIn('android:fillColor="#00000000"', light)
        for output in outputs.values():
            ET.fromstring(output)

    def test_check_detects_stale_generated_resources_without_writing_them(self):
        for path, text in colors.outputs().items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        native = self.root / 'app/androidApp/src/main/res/values/struct_colors.xml'
        before = native.read_bytes()
        self.source.write_text(self.source.read_text().replace('0xFF6D5E0F', '0xFF123456'))
        with patch('sys.argv', ['sync-design-colors.py', '--check']), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(SystemExit, 'generated colors are stale'):
                colors.main()
        self.assertEqual(before, native.read_bytes())

    def test_audit_rejects_scattered_literals_but_allows_theme_and_transparency(self):
        path = self.root / 'app/shared/src/commonMain/kotlin/dev/example/Screen.kt'
        path.parent.mkdir(parents=True)
        path.write_text('val bad = Color(0xFF123456)\nval white = Color.White\n')
        self.assertEqual(2, len(colors.audit()))
        path.write_text('val inherited = MaterialTheme.colorScheme.primary\nval clear = Color.Transparent\n')
        self.assertEqual([], colors.audit())
        self.source.write_text(self.source.read_text().replace('val black = Color(0xFF000000)', 'val black = Color(0xFFFFFFFF)'))
        self.assertIn('duplicate ARGB', '\n'.join(colors.audit()))
