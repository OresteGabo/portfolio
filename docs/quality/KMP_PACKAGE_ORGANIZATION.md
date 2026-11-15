# KMP package organization verification

15 November 2026; baseline `529f65b`. [Current concise package/module guide](../architecture/PACKAGE_GUIDE.md).

## Scope and decisions

Inspection covered module dependencies, owned package paths across common/platform/test source sets, transport/feature callers, public versus internal usage, and native hosts/scripts. The Gradle graph is acyclic and module responsibilities are meaningful; creating additional modules would add build configuration without demonstrated isolation benefits here.

The giant legacy `investments` namespace made transport and feature ownership hard to discover. This change performs **16 cohesive moves**, with package/import updates in **63 tracked Kotlin files**:

- Four core common transport/configuration files → `dev.orestegabo.portfolio.network`.
- Three platform HTTP engine files → the matching network package in Android/JVM/iOS source sets.
- Four common network tests and the Android host transport test → matching network test packages.
- Three account workflow/state collaborators → `features.transactions`, `features.shares`, `features.documents` in shared common code.
- The cross-feature data-capability test → shared `features`.

The model and all consumers use explicit imports. No aliases or duplicate implementations remain at old paths. Truly shared JSON values, financial rules and diagnostics remain in core; no peer workflow dependency was added. Domain/transport separation is package-level within core, not a claim that core is an entirely pure domain module.

Workflow classes retain internal visibility. `createPlatformHttpEngine` becomes internal in its expect and all three actual declarations: inspection found no caller outside core. Public ports/client types and Android `PlatformBuild` still serve cross-module consumers. `createPublicQuoteHttpEngine` remains internal; session-cookie storage remains private.

All implementation bodies are unchanged after normalizing package/import metadata and the one factory visibility change. This was checked against a beginning-of-step source manifest for every existing Kotlin source, and against HEAD for every staged Kotlin source. Financial calculations, PDF/parser bytes and rules, persistence, lifecycle, UI behavior, IDs, settings keys and API payloads were not edited. The local Spring check's main-class name remains unchanged; its imports were updated without changing the Gradle task.

The earlier uncommitted responsibility refactor remains in the working tree. Its affected files received necessary import updates locally, but only package/import changes against committed source are included in this commit. An exported staged tree was built independently to verify that it does not require the unstaged implementations. Initial renamed-file staging needed an explicit Git add flag; the complete corrected export below is the one used for final checks.

## Package gate

`scripts/check-kmp-packages.py` uses only Python's standard library. It checks project-owned Kotlin package/path alignment, concrete Gradle project dependencies for cycles, the migrated import boundaries and internal workflow visibility. Three regression tests cover source-set paths, acyclic/cyclic module graphs, and reverse/peer imports. The existing Android CI job runs the gate; no new Gradle configuration is required.

The migrated package dependency direction is acyclic when qualified by module: shared composition → shared feature workflows → core network → core shared values. Core has no dependency on app features. Remaining legacy packages are documented rather than moved into arbitrary folders. The checker intentionally governs selected boundaries, not every historical package or fully qualified Kotlin reference.

## Executed verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`. Isolated builds also set `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk`.

Working-tree build/test command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Result: exit 0, `BUILD SUCCESSFUL in 42s`; includes Android instrumentation **compilation**, not device execution. Log: `/tmp/inzira-packages-working-build.log`.

Native affected transport tests and complete shared suite:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --tests '*PortfolioApiTest' --tests '*PortfolioDataApiTest' --tests '*PublicEquityQuotesTest' --tests '*BackendOriginTest' :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Result: exit 0, `BUILD SUCCESSFUL in 1m 17s`; **15 relocated core native tests + 445 shared native tests pass**. Working-tree host XML: **97 core JVM + 98 core Android host + 422 shared host tests pass**, with zero failures/errors/skips. Log: `/tmp/inzira-packages-working-native.log`.

### Independent staged implementation

The staged source was exported using `git write-tree` and `git archive` into a temporary directory. The host/build/compilation command above was rerun with `--project-dir "$audit_export"` and the SDK variable, additionally including `:app:androidApp:lintRelease`. The native command was also rerun against that same export.

Final export: `/tmp/inzira-packages-index.sd0bZM`. Build/host/lint/device+simulator compilation: exit 0, `BUILD SUCCESSFUL in 1m 13s`; **97 core JVM + 98 core Android host + 411 shared host tests pass**. Native: exit 0, `BUILD SUCCESSFUL in 28s`; **15 selected core native + 434 shared native tests pass**. All XML counts have zero failures/errors/skips. Logs: `/tmp/inzira-packages-index-build-final.log`, `/tmp/inzira-packages-index-native.log`.

The source variants in that export exclude earlier uncommitted implementation changes. This accounts for the smaller shared-test count than the working tree, rather than tests being disabled for this refactor. The final staged sources were compared byte-for-byte with the verified export before committing.

### Static and engine/package checks

Executed in both the working tree and isolated export:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both locations pass the package gate, design-colour audit, **16 Python tests** (including three package-gate tests), **84 Node parser/engine tests**, and diff whitespace checks. The package gate checks 374 owned Kotlin source/test files in the working tree and 351 in the isolated export. Logs: `/tmp/inzira-packages-index-packages.log`, `/tmp/inzira-packages-index-python.log`, `/tmp/inzira-packages-index-node.log`, `/tmp/inzira-packages-index-colors.log`.

No configured Detekt/ktlint task exists. Release lint passes with baseline warnings; warning-free output is not claimed. No production assets were regenerated.

The known separate core-native share-cost equality failure recorded in the earlier audit remains unchanged. The native command here selects the relocated transport classes; it is not a full core-native green claim. All core JVM/Android host tests and complete shared host/native suites are included. No physical-phone UI session, connected instrumentation execution, production backend action, signing/distribution or live push test was performed. Package/body comparisons and these scoped checks provide the stated equivalence evidence, not exhaustive runtime proof.

Commit message: `refactor(kmp): improve package and module organization`. Keep prior uncommitted implementation changes and unrelated documentation out of this commit.
