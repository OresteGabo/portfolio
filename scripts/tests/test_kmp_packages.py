import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("kmp_packages", Path(__file__).parents[1] / "check-kmp-packages.py")
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


class KmpPackageChecksTest(unittest.TestCase):
    def test_package_locations_cover_common_platform_and_test_sets(self):
        for source_set in ("commonMain", "androidMain", "iosMain", "commonTest", "androidHostTest"):
            path = f"core/src/{source_set}/kotlin/dev/orestegabo/portfolio/network/Client.kt"
            self.assertTrue(checks.package_matches(path, checks.NETWORK))
            self.assertFalse(checks.package_matches(path, checks.PREFIX + ".investments"))

    def test_module_cycle_is_rejected_but_shared_dependency_is_allowed(self):
        graph = {"android": {"shared"}, "shared": {"core"}, "server": {"core"}, "core": set()}
        self.assertFalse(checks.cyclic(graph))
        graph["core"].add("shared")
        self.assertTrue(checks.cyclic(graph))

    def test_migrated_packages_reject_reverse_and_peer_feature_dependencies(self):
        prefix = checks.PREFIX
        self.assertIsNone(checks.ownership_error(":core", checks.NETWORK, prefix + ".investments.JsonRecord"))
        self.assertIsNotNone(checks.ownership_error(":core", prefix + ".investments", checks.NETWORK + ".PortfolioApi"))
        self.assertIsNotNone(checks.ownership_error(":core", checks.NETWORK, prefix + ".features.shares.PortfolioShareOrders"))
        feature = prefix + ".features.transactions"
        self.assertIsNone(checks.ownership_error(":app:shared", feature, checks.NETWORK + ".PortfolioDataApi"))
        self.assertIsNone(checks.ownership_error(":app:shared", feature + ".editor", feature + ".OtherWorkflow"))
        self.assertIsNotNone(checks.ownership_error(":app:shared", feature, prefix + ".investments.PortfolioModel"))
        self.assertIsNotNone(checks.ownership_error(":app:shared", feature, prefix + ".features.documents.PortfolioStatements"))


    def test_common_main_rejects_sdk_and_native_imports_in_any_package(self):
        for package in ("dev/orestegabo/portfolio", "androidx/compose/material/icons"):
            path = f"app/shared/src/commonMain/kotlin/{package}/Example.kt"
            for imported in ("android.content.Context", "java.math.BigDecimal", "javax.crypto.Cipher", "platform.Foundation.NSData", "kotlinx.cinterop.*"):
                self.assertIsNotNone(checks.common_platform_import_error(path, imported))

    def test_platform_and_shared_jvm_source_roots_keep_their_available_apis(self):
        for source_set, imported in (("androidMain", "android.content.Context"), ("iosMain", "platform.Foundation.NSData"), ("jvmMain", "java.math.BigDecimal"), ("jvmShared", "java.math.BigDecimal")):
            self.assertIsNone(checks.common_platform_import_error(f"core/src/{source_set}/kotlin/Example.kt", imported))

        self.assertIsNone(checks.common_platform_import_error("core/src/jvmMain/kotlin/commonMain/Example.kt", "java.math.BigDecimal"))

    def test_androidx_multiplatform_apis_are_not_mistaken_for_android_sdk(self):
        path = "app/shared/src/commonMain/kotlin/Example.kt"
        for imported in ("androidx.compose.ui.Modifier", "androidx.lifecycle.ViewModel", "kotlinx.coroutines.Dispatchers", "kotlin.native.HiddenFromObjC"):
            self.assertIsNone(checks.common_platform_import_error(path, imported))
