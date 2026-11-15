# KMP encapsulation review and changes

Reviewed 15 November 2026; baseline `89e3ef2`. Mobile code only. Earlier uncommitted decomposition work is preserved and excluded from this focused commit.

## Findings and ownership decisions

| Finding | Decision / result |
|---|---|
| **High — authenticated identity was mutable through `model.user.put(...)` despite a private setter.** | The session owner now stores a detached read-only identity snapshot. Callers can read identity/role fields but cannot edit the published record. Login/session restoration and logout remain the writers. |
| **High — ledger, pending-order and statement state exposed mutable record elements behind `List<JsonRecord>`.** | Those publication points now own detached record snapshots in read-only list containers. Private setters alone were insufficient: callers could change amounts/versions with `put`, or cast a list to a mutable collection. Both mutation paths are rejected now. Previously published snapshots remain stable across refresh/clear. |
| **High — guest PDF confirmation could survive a different target purchase.** | `confirmed` and `targetId` have private setters. Explicit `confirmDetails`/`selectTarget` operations enforce review availability and revoke confirmation when the target changes. Reselecting the same target preserves confirmation. The repository still validates the selected ID and financial linkage on save; the selection method is not a replacement for those checks. |
| **Medium — parser-owned JSON could remain aliased to the published guest review.** | The review is detached before publication, and a duplicate-document result is also read-only. Form fields remain editable detached drafts; they cannot rewrite the retained review. |
| **Medium — the concrete model HTTP client was exposed publicly.** | Its getter is now `internal`, with its private setter retained. Some committed legacy shared UI callers still use it, so making it private now would require a different extraction. Android host consumers no longer receive this implementation capability through the public model API. |
| **No production `MutableStateFlow` exposure found.** | The app uses Compose delegated state. No unnecessary Flow migration or synthetic getter/setter layer was introduced. |
| **Existing appropriate encapsulation.** | Most ViewModel/feature state already has private setters. Cookies, repository mutation/storage internals and original selected PDF bytes are private; repository writes are serialized and validated. Existing financial-rule constructors validate amounts/rates/frequencies. These boundaries were preserved. |
| **Legitimate mutable inputs retained.** | Mutable JSON request builders, local form drafts, progress callbacks, UI notice/navigation inputs, native bridge configuration and explicit settings writes serve real callers. Public error assignment still passes through the existing safe-message boundary. These were not made private mechanically. |
| **Remaining DTO/alias debt.** | `PortfolioSnapshot`, other legacy JSON-based projections, migration rows and some bridge collections are not universally deeply immutable. A `val`/`List` type alone does not freeze nested records. This change hardens the named owners, not every historical DTO or financial read model; those remaining publication points need their own characterized migration. |
| **Native buffer ownership retained.** | Preview byte arrays are used by native rendering and are cleared by the existing owner; private original-PDF copies remain separate. No copy-on-every-getter buffer API was introduced. |

Inspection traced writers and readers in common code, platform adapters, Compose screens, native tests and Android instrumentation sources. In particular, all callers of the guest confirmation/target setters were updated, and cross-module accesses to the model client were checked. No backend or website source was changed.

## Implementation

`core/.../JsonRecord.kt` adds two opt-in publication operations:

- `readOnlyCopy()` takes a detached JSON snapshot and rejects later `put` calls on that record. Its ownership marker is private and is **not serialized**.
- `JsonRecord.readOnlyList(records)` owns the list and snapshots its elements. A common Kotlin `AbstractList` exposes the read API without exposing the mutable backing collection. No platform-specific collection wrapper or additional dependency is needed.

Existing constructors still create mutable builders. `JsonRecord(snapshot.toString())` creates an editable draft. Nested-object/row reads remain detached editable copies, as before; editing those copies does not change the published snapshot. Unknown keys, numeric representations, booleans, nulls and nested facts survive the round trip. This preserves the existing `JsonRecord` signatures and wire/persistence representation while tightening selected owner boundaries.

Account workflow publication points include list reloads, selected statement review, document review, reconciliation updates, statement checking and newly created statement bonds. Callback results that are independent command results remain editable; they do not alias owned state. Snapshot copies occur when publishing/replacing state, not on each getter or composition.

`PortfolioModel.user` retains its public read/private-write shape with a private Compose backing value and snapshotting setter. The client getter uses the smallest visibility supported by its remaining shared-module callers. State APIs remain Kotlin properties and explicit domain/user intentions; no Java-style accessor facade was generated.

`GuestPdfImport` keeps its public read properties. Confirming true requires a current review and private PDF; confirmation false can always be withdrawn. Concrete target selection requires a current review and a nonblank ID; clearing the selection is safe even after cancellation. A changed target resets confirmation, so the existing save guard blocks reuse of an old approval. Clear/new selection reset state as before. UI handlers ignore stale confirmation/selection events after the review closes, and all native/test callers now use the explicit methods.

These are intentional changes for previously uncontrolled/invalid mutations. Existing valid edits, saves, refreshes, cancellations, evidence linking and session operations keep their existing commands and validation. Financial formulas, parser logic, private PDF contents, repository schema/transactions, API payloads, IDs and settings keys are unchanged.

## Regression coverage

Nine added tests cover:

1. Detached record ownership, aliased nested JSON input, unknown-field/wire preservation, rejected root mutation and valid editable copies.
2. Detached nested reads that cannot mutate an owner.
3. Read-only containers and records that survive mutation of their original inputs.
4. Ledger snapshot protection, stable older snapshots and valid save/refresh.
5. Pending-order projection sharing the same protected snapshot and valid save.
6. Every statement publication path, detached callback results and clear.
7. Rejected confirmation/selection without a PDF, rejected blank selection without state damage, target-change invalidation, same-target idempotence and clear.
8. Parser/form edits cannot alter a published guest review.
9. Authenticated account ID/role mutation is rejected; logout clears the current identity while an older snapshot remains stable.

Existing tests were retained; only direct guest setter calls were migrated to their replacement operations. No assertion, financial comparison tolerance or persistence validation was weakened.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`. Final working-tree command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Result: exit 0, `BUILD SUCCESSFUL in 49s`; **100 core JVM + 101 core Android host + 428 shared host tests pass**, zero failures/errors/skips. Android debug and instrumentation APK compilation, release lint and both iOS compilations pass. Log: `/tmp/inzira-encapsulation-working-build-final.log`.

Native affected JSON/financial/transport classes and the complete shared suite:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --tests '*JsonRecordTest' --tests '*PortfolioApiTest' --tests '*PortfolioDataApiTest' --tests '*InvestmentLedgerTest' --tests '*InvestmentInsightsTest' --tests '*ShareOrderEstimateTest' --tests '*PortfolioSnapshotTest' --tests '*BondMathTest' :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Result: exit 0, `BUILD SUCCESSFUL in 1m 4s`; **52 selected core native + 451 shared native tests pass**, zero failures/errors/skips. Log: `/tmp/inzira-encapsulation-working-native-final.log`.

### Exact proposed-commit verification

The staged tree was exported using `git write-tree` / `git archive`, excluding the earlier uncommitted refactor. Only the new identity/client encapsulation hunks of the already-dirty model were staged. The two commands above were repeated against that export with `--project-dir "$audit_export"` and `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk`.

Final export: `/tmp/inzira-encapsulation-index.yXGCW7`. Host/build/lint/compilation command: exit 0, `BUILD SUCCESSFUL in 56s`; **100 core JVM + 101 core Android host + 417 shared host tests pass**. Native command: exit 0, `BUILD SUCCESSFUL in 30s`; **52 selected core native + 440 shared native tests pass**. All counts have zero failures/errors/skips. The 11-test shared-count difference from the working tree belongs to the excluded earlier refactor. Logs: `/tmp/inzira-encapsulation-index-build-final.log`, `/tmp/inzira-encapsulation-index-native.log`.

Static/package/script checks in both the working tree and isolated export:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both locations pass the package gate, design-colour audit, **16 Python tests**, **84 Node engine/parser tests**, and diff whitespace checks. Logs: `/tmp/inzira-encapsulation-index-packages.log`, `/tmp/inzira-encapsulation-index-colors.log`, `/tmp/inzira-encapsulation-index-python.log`, `/tmp/inzira-encapsulation-index-node.log` and working-tree equivalents. Final staged source was compared byte-for-byte with the verified export before committing.

No configured Detekt/ktlint task exists. Release lint has baseline warnings; warning-free output is not claimed. No production assets were regenerated. No connected Android instrumentation execution, physical-device UI run, signing/distribution, live-account write or backend deployment was performed.

The separate core-native `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales` decimal equality failure recorded by the prior audit remains unchanged and is not among the eight selected affected-core classes above. The full core JVM/Android host suites and full shared host/native suites pass; this is not an all-core-native/all-task green claim. All final scoped verification must pass before committing.

Commit: `refactor(kmp): strengthen encapsulation`. Earlier unrelated implementation/documentation changes remain uncommitted.
