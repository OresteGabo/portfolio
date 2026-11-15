#!/usr/bin/env python3
"""Check migrated KMP package ownership without imposing a whole-app architecture rewrite."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "dev.orestegabo.portfolio"
NETWORK = PREFIX + ".network"
FEATURES = PREFIX + ".features"
VALUES = {PREFIX + ".investments." + name for name in ("JsonRecord", "JsonList", "UnlockPerformance")}
MODULES = {name: ROOT / path for name, path in {
    ":core": "core", ":app:shared": "app/shared", ":app:androidApp": "app/androidApp", ":server": "server"
}.items()}


def package_matches(path, package):
    """The suffix after kotlin/ must match the declared package, including test/actual sources."""
    parts = Path(path).parts
    if "kotlin" not in parts:
        return True
    return tuple(parts[parts.index("kotlin") + 1:-1]) == tuple(package.split("."))


def cyclic(graph):
    active, done = set(), set()

    def visit(node):
        if node in active:
            return True
        if node in done:
            return False
        active.add(node)
        if any(visit(child) for child in graph.get(node, ())):
            return True
        active.remove(node)
        done.add(node)
        return False

    return any(visit(node) for node in graph)


def ownership_error(module, package, imported):
    if not imported.startswith(PREFIX + "."):
        return None
    in_network = package == NETWORK or package.startswith(NETWORK + ".")
    if module == ":core":
        if imported.startswith(FEATURES + "."):
            return "core cannot depend on app features"
        if in_network and not (imported.startswith(NETWORK + ".") or imported in VALUES):
            return "network may use shared values/diagnostics, not investment-feature implementations"
        if not in_network and imported.startswith(NETWORK + "."):
            return "core investment rules must not import network implementation"
    if module == ":app:shared" and package.startswith(FEATURES + "."):
        feature_root = ".".join(package.split(".")[:len(FEATURES.split(".")) + 1])
        if imported.startswith(feature_root + ".") or imported.startswith(NETWORK + ".") or imported in VALUES:
            return None
        return "migrated feature workflows cannot import composition roots or unrelated features"
    return None


def main():
    errors, checked = [], 0
    graph = {}
    for module, directory in MODULES.items():
        build = (directory / "build.gradle.kts").read_text()
        graph[module] = set(re.findall(r'project\("(:[^\"]+)"\)', build))
        for path in directory.glob("src/*/kotlin/**/*.kt"):
            text = path.read_text()
            declaration = re.search(r"^package ([\w.]+)", text, re.M)
            if not declaration or not declaration[1].startswith(PREFIX):
                continue
            package = declaration[1]
            checked += 1
            if not package_matches(path, package):
                errors.append(f"{path.relative_to(ROOT)}: package/path mismatch ({package})")
            if "/commonMain/" not in str(path):
                continue
            for imported in re.findall(r"^import ([\w.*]+)", text, re.M):
                problem = ownership_error(module, package, imported)
                if problem:
                    errors.append(f"{path.relative_to(ROOT)}: {problem}: {imported}")
            if module == ":app:shared" and package.startswith(FEATURES + "."):
                if re.search(r"^class Portfolio(?:CashLedger|ShareOrders|Statements)\b", text, re.M):
                    errors.append(f"{path.relative_to(ROOT)}: workflow implementation must stay internal")
    if cyclic(graph):
        errors.append("Gradle project dependency cycle")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"KMP packages: {checked} source/test files match paths; migrated ownership and module DAG pass.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
