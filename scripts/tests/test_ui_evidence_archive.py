import importlib.util
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('ui_evidence', Path(__file__).parents[1] / 'archive-ui-evidence.py')
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class UiEvidenceArchiveTests(unittest.TestCase):
    def test_compiler_names_and_contents_are_preserved_without_unrelated_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            reports = {
                'app/shared/build/reports/compose/Portfolio_app:shared-classes.txt': b'compiler output\n',
                'app/shared/build/reports/compose-metrics/Portfolio_app:shared-module.json': b'{"total":2}',
                'app/shared/build/reports/tests/unit/index.html': b'unit results',
                'core/build/reports/tests/jvmTest/index.html': b'core results',
                'app/androidApp/build/reports/lint-results-debug.xml': b'<issues/>',
            }
            for name, content in reports.items():
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(content)
            (root / '.env').write_text('must not be archived')
            output = root / 'upload' / 'kmp-ui-evidence.tar.gz'
            self.assertEqual(len(reports), evidence.archive(root, output))
            with tarfile.open(output) as archive:
                self.assertEqual(set(reports), set(archive.getnames()))
                for name, content in reports.items():
                    self.assertEqual(content, archive.extractfile(name).read())

    def test_missing_reports_still_produce_a_valid_archive_after_a_failed_build(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = root / 'kmp-ui-evidence.tar.gz'
            self.assertEqual(0, evidence.archive(root, output))
            with tarfile.open(output) as archive:
                self.assertEqual([], archive.getnames())
