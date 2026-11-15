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
