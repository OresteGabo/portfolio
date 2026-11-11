import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class IosFrameworkBuildTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name)
        (self.project / 'scripts').mkdir()
        shutil.copyfile(ROOT / 'scripts/build-ios-framework.sh', self.project / 'scripts/build-ios-framework.sh')
        self.log = self.project / 'build.log'
        wrapper = self.project / 'gradlew'
        wrapper.write_text('#!/bin/sh\nprintf "%s|%s\\n" "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED-unset}" "$*" >> "$BUILD_LOG"\nexit "${FAKE_EXIT:-0}"\n')
        wrapper.chmod(0o755)
        self.env = dict(os.environ, CONFIGURATION='Debug', SDK_NAME='iphoneos26.2', BUILD_LOG=str(self.log))
        self.env.pop('OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED', None)

    def run_build(self, existing=False, override=False, fail=False):
        if existing:
            binary = self.project / 'app/shared/build/xcode-frameworks/Debug/iphoneos26.2/Shared.framework/Shared'
            binary.parent.mkdir(parents=True)
            binary.write_bytes(b'framework')
        if override:
            self.env['OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED'] = 'YES'
        if fail:
            self.env['FAKE_EXIT'] = '7'
        return subprocess.run(['/bin/sh', self.project / 'scripts/build-ios-framework.sh'], env=self.env, capture_output=True)

    def test_ide_build_recovers_missing_framework_after_clean(self):
        self.assertEqual(self.run_build(override=True).returncode, 0)
        self.assertEqual(self.log.read_text().strip(), 'unset|:app:shared:embedAndSignAppleFrameworkForXcode')

    def test_ide_prebuilt_framework_only_syncs_resources(self):
        self.assertEqual(self.run_build(existing=True, override=True).returncode, 0)
        self.assertEqual(self.log.read_text().strip(), 'YES|:app:shared:syncComposeResourcesForIos')

    def test_xcode_build_always_checks_framework_for_source_updates(self):
        self.assertEqual(self.run_build(existing=True).returncode, 0)
        self.assertEqual(self.log.read_text().strip(), 'unset|:app:shared:embedAndSignAppleFrameworkForXcode')

    def test_framework_failure_fails_xcode_phase(self):
        self.assertEqual(self.run_build(override=True, fail=True).returncode, 7)
