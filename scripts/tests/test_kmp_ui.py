import importlib.util
import json
from pathlib import Path
import tempfile
import shutil
import unittest

spec = importlib.util.spec_from_file_location('ui_guard', Path(__file__).resolve().parents[1] / 'check-kmp-ui.py')
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)


class UiEvidenceTest(unittest.TestCase):
    def test_inventory_has_existing_test_methods(self):
        self.assertEqual([], ui.validate(ui.ROOT, require_goldens=False))

    def check_result(self, child, expected):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docs/quality').mkdir(parents=True)
            (root / 'docs/quality/ui-coverage.json').write_text(json.dumps({'flows': [{'class': 'ScreenTest', 'method': 'opens'}]}))
            (root / 'result.xml').write_text(f'<testsuite><testcase classname="example.ScreenTest" name="opens">{child}</testcase></testsuite>')
            self.assertEqual(expected, len(ui.verify_results(root, root)))

    def test_passing_evidence(self):
        self.check_result('', 0)

    def test_skips_are_not_success(self):
        self.check_result('<skipped/>', 1)

    def test_failure_is_not_success(self):
        self.check_result('<failure message="native crash"/>', 2)

    def test_missing_evidence_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(len(json.loads((ui.ROOT / 'docs/quality/ui-coverage.json').read_text())['flows']),
                             len(ui.verify_results(ui.ROOT, Path(directory))))

    def golden_errors(self, mutate):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docs/quality').mkdir(parents=True)
            (root / 'docs/quality/ui-coverage.json').write_text('{"flows": []}')
            dest = root / ui.ASSETS
            dest.mkdir(parents=True)
            manifest = json.loads((ui.ROOT / ui.ASSETS / 'manifest.json').read_text())
            manifest['profiles'] = [p for p in manifest['profiles'] if p['id'] == 'small']
            manifest['goldens'] = [g for g in manifest['goldens'] if g['file'].startswith('small-')]
            for golden in manifest['goldens']:
                shutil.copyfile(ui.ROOT / ui.ASSETS / golden['file'], dest / golden['file'])
            mutate(manifest)
            (dest / 'manifest.json').write_text(json.dumps(manifest))
            return ui.validate(root)

    def test_missing_theme_is_rejected(self):
        self.assertTrue(self.golden_errors(lambda m: m['goldens'].pop()))

    def test_changed_hash_is_rejected(self):
        def mutate(manifest):
            manifest['goldens'][0]['sha256'] = 'unreviewed'
        self.assertTrue(any('hash differs' in e for e in self.golden_errors(mutate)))

    def test_changed_dimensions_are_rejected(self):
        def mutate(manifest):
            manifest['profiles'][0]['width'] += 1
        self.assertTrue(any('dimensions differ' in e for e in self.golden_errors(mutate)))
