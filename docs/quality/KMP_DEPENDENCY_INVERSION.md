# Dependency Inversion review

Reviewed against `813a8c1` and the working tree on 15 November 2026. Scope: KMP mobile code and manual dependency wiring. No backend, website, financial formulas, persisted schemas, parser assets, dependencies or Gradle configuration changes. Earlier unfinished implementation extractions are excluded from this commit.

## Architecture and findings

The application uses constructor/function injection, Compose composition locals and `expect`/`actual` factories; there is no Koin/Dagger container or separate dependency-resolution task. Android host → shared app → core remains the module direction; iOS embeds the shared framework. Core includes portable financial rules and a transport package, not an entirely infrastructure-free domain module.

| Area inspected | Finding / action |
|---|---|
| ViewModel session dependency | `PortfolioModel` accepted a factory returning concrete `PortfolioApi` and retained that concrete type. Injecting a different session implementation required an HTTP client even for application startup/lifecycle tests. **Depend on `PortfolioSessionApi` instead.** Keep the same lazy factory and lifecycle owner. |
| Cookie format inside application policy | Saved-sign-in recovery inspected `sessionSnapshot()` JSON's `cookies` array to decide whether to check the server session or log in again. **Move that exact presence query to `PortfolioApi.hasSession()`**, behind the session contract. Snapshots are opaque to the model. |
| Migration request adapter | `ApiGuestMigrationRemote` needed origin and data requests, but depended on a client with cookie restoration, clearing and closing capabilities. **Accept `OriginBoundDataApi`** without session ownership. Origin binding stays mandatory before I/O. |
| Import/notification/report collaborators | The committed `PortfolioImportHost`, `PortfolioNotificationHost` and `PortfolioReports` provider also retained concrete client types, although their implementations use only data operations. **Depend on existing `PortfolioDataApi`**. Only signature/import substitutions are staged; older implementation extractions remain excluded. |
| Evidence sharing | `shareInvestmentDocument` required concrete `PortfolioApi` although it only downloads bytes. **Accept existing `PortfolioDataApi`**; no extra download-service wrapper is needed. |
| Guest persistence | Existing read/import/full repository capabilities and snapshot/document storage ports already separate application workflows from native storage. Keep atomic persistence/recovery ownership in the common implementation; no Room/SQLDelight database is present to introduce a new database abstraction around. |
| PDF parsing / platform APIs | `InvestmentDocumentParser`, `PdfPicker`, page-rendering ports and native factories already isolate genuine platform differences. Keep those boundaries and OS resource lifecycles. No additional parser/engine factory interface is needed. |
| Financial rules | Typed terms, dates and pure calculation functions do not depend on Android/Compose/Ktor. Keep them concrete and explicit rather than adding injectable calculator interfaces without a substitutability need. |
| Environment seams | Existing clock, dispatcher, assets and guest time/ID/fetch injections are useful. Preserve these, including Kotlin's standard monotonic `TimeSource`; no new app-wide clock/filesystem service locator. |
| Global bridges | Native inbox, push, widget and navigation bridges still connect host lifecycle events to the app. This slice does not rewrite those platform entry points or claim all global state has been removed. A future ownership change needs scoped lifecycle tests, not a registry replacing one global with another. |

## Implemented contracts and composition

`core/…/network/PortfolioSessionApi.kt` contains two portable capability contracts:

- `OriginBoundDataApi : PortfolioDataApi` adds the immutable client origin. Request adapters can enforce origin binding without being granted session lifecycle operations.
- `PortfolioSessionApi : OriginBoundDataApi` exposes session presence, opaque snapshot/restore, clear and close operations required by the application session owner.

These contracts contain no Ktor client/engine, cookie, Android, Compose or native resource types. The existing JSON/byte request contract is retained. The session port is deliberately broader than the data port because the session/lifecycle coordinator genuinely needs those operations; feature workflows continue receiving data-only capabilities.

`PortfolioApi` implements the session port. Its request, response, cookie-storage, validation, serialization and close implementations remain unchanged except for declaration-level overrides and the new presence query. That query evaluates the same filtered session snapshot/cookie-list expression previously used by the model; it is **not** evidence that the server has authenticated the user. Server identity checking and credential fallback remain in the model.

`createPortfolioSession(origin)` is the stateless default composition function in the transport implementation file. It creates the same concrete client with the same platform engine/configuration and returns the contract. The model's factory parameter now returns `PortfolioSessionApi` and defaults to that function. Existing callers supplying `PortfolioApi` factories remain source-compatible through normal return-type subtyping.

This constructor default is a compatibility/convenience wiring path, not a new service locator: there is no global client, registry or cached resolution. Tests inject another implementation directly. The existing `App` composition root, native factories, guest-mode gating and settings/vault wiring need no registrations or wrappers.

## Ownership and behavior preservation

- Client construction remains lazy: deferred account startup does not resolve a client. Guest mode keeps its existing account-client gate.
- The model owns client replacement and closing; feature adapters do not. Origin replacement constructs the next client, clears/closes the previous one and persists/checks the new origin in the same order.
- Saved-sign-in recovery restores opaque client state and asks `hasSession()` before deciding whether to check the server or perform the existing login fallback. It still requires a server-validated matching account. Cookie eligibility, expiry, snapshot representation and persisted vault wrapper remain unchanged for the real client.
- Migration origin checks, request paths/payloads, PDF chunking and error behavior are unchanged. Evidence URL validation, authenticated download and native sharing are unchanged.
- Only dependency types/default factory wiring and the equivalent session-presence expression change in the already-dirty model; unrelated ViewModel extractions remain unstaged. The three additional feature contracts receive only data-type substitutions, preserving their committed method bodies. Source comparisons validate these boundaries and unchanged HTTP implementation bodies.

No inheritance framework, DI library, new module, repository facade or one-interface-per-class convention is introduced. Remaining coupling is explicit: older UI still uses the model's internal session/data access; application error handling still uses the existing shared `ApiException` status convention. Typed authentication outcomes and host-global ownership would be separate behavior-sensitive changes.

## Targeted tests

Six new tests:

1. Real HTTP implementation through `PortfolioSessionApi`: empty/present session, snapshot restore and clear, with unchanged snapshot representation.
2. Unrelated/expired cookies do not count as session credentials, and origin-bound restore is still rejected before transport activity.
3. Deferred startup and origin replacement with an in-memory session implementation: lazy resolution, exact request routing, previous-client clear/close and final owner cleanup. No HTTP engine is constructed.
4. Normal startup resolves the injected implementation and finishes public-asset/session initialization without a transport engine.
5. Saved-sign-in recovery accepts client state that is not JSON and uses the port's presence decision; rejected login still deletes the saved sign-in and leaves the lock flow as before.
6. Migration request adapter accepts only origin-bound data access, rejects a mismatched origin before I/O and uses the unchanged status endpoint for a valid binding.

Existing transport/session/security, guest/account, document sharing, financial, storage and native suites remain. No assertion, financial tolerance, parser or persistence validation is weakened. The in-memory session is a test implementation of a production capability, not a new production service abstraction.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Focused command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :app:shared:testAndroidHostTest --tests '*SessionDependencyBoundaryTest' --tests '*GuestMigrationTest' --tests '*InvestmentDocumentShareTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 13s`; log `/tmp/inzira-dip-focused.log`. Includes the full core JVM suite with the new session contracts.

Full working-tree command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 54s`; **102 core JVM + 103 core Android host + 466 shared host tests**, zero failures/errors/skips. Android build/instrument APK compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-dip-working-build.log`.

Exact staged verification excludes older unfinished work. Only the model/feature data-contract hunks were staged from their already-dirty files. The first isolated attempt exposed three additional committed feature contracts still naming `PortfolioApi`; compilation failed until their import/type declarations were narrowed to `PortfolioDataApi`. This was resolved without staging their unrelated implementation changes. The failed attempt is recorded in `/tmp/inzira-dip-index-build.log`, not claimed as passing.

The final eleven-file source/test/report tree was exported using `git write-tree` / `git archive` to `/tmp/inzira-dip-index.0bRpZ1`. The full command above was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$audit_export"`.

Exit 0, `BUILD SUCCESSFUL in 1m 1s`; **102 core JVM + 103 core Android host + 455 shared host tests**, zero failures/errors/skips. Android build/lint/instrumentation compilation and both iOS compilations pass. Log `/tmp/inzira-dip-index-build-final.log`. The working tree's additional 11 tests belong to excluded earlier refactoring.

Affected core-native contracts and the full shared native suite:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --tests '*PortfolioSessionApiTest' --tests '*PortfolioApiTest' --tests '*PortfolioDataApiTest' :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 46s`; **11 selected core native tests + 474 shared native tests**, zero failures/errors/skips. Log `/tmp/inzira-dip-index-native.log`. Includes the new real session contract and infrastructure-free startup/lifecycle tests on Kotlin/Native.

Static/engine checks in both working and final isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Package ownership/module DAG and color checks pass, **16 Python tests and 84 Node tests** pass, final whitespace checks pass. Logs `/tmp/inzira-dip-working-{packages,colors,python,node}.log` and `/tmp/inzira-dip-index-{packages,colors,python,node}-final.log`. Source normalization confirms unchanged transport implementation bodies after removing declaration overrides and the new presence/factory functions; feature hunks are type/import substitutions only. Staged Kotlin matches the final verified export byte-for-byte.

DI/startup verification consists of the targeted normal/deferred/replacement/cleanup tests, existing session/guest tests, and compilation of actual Android/iOS composition/factory callers. No container-specific task exists. No configured Detekt/ktlint task exists; baseline compiler/lint warnings remain. No physical-device UI startup, connected Android instrumentation execution, signed Xcode distribution, live account write or backend deployment occurred. Instrument APK compilation is not test execution.

The previously documented separate full-core-native share-cost floating-point equality failure is unchanged and outside the three selected affected native contract classes. Full core JVM/Android host and full shared native results are not an all-core-native/all-task green claim.

Commit only this verified slice as `refactor(kmp): improve dependency inversion`; preserve older unfinished work.
