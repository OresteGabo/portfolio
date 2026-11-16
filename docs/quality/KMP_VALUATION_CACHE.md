# Stable dashboard valuation

The dashboard previously published purchase cost (including commission) while closing prices were loading, then replaced it with market value. This looked like rounding when market values happened to end in zeros.

Authenticated sessions now restore a bounded, account-and-backend-scoped closing-price cache before starting portfolio synchronization. Android uses the existing private connection preferences; iOS uses the existing UserDefaults settings port. Only ticker, name, closing price, original publication date and stale indicator are stored. Holdings, balances, PDFs and credentials are not duplicated in this cache. Account deletion removes its cache; logout clears in-memory prices. Guest pricing remains on its existing separate path.

Valuations still derive from the current holdings and bond records. A purchase, sale or pending investment changes the total immediately using those records; no independent cached total can override the new quantities. The existing bond principal and financial formulas are unchanged. This does not add offline access to authenticated portfolio records.

Prices are checked after 15 minutes, or immediately on an explicit pull-to-refresh. A source publication date is never replaced by the fetch date. Restored prices are labeled cached. Network failures, empty/malformed boards, future dates and older publication dates retain the last usable board. Requests are serialized, cancellation propagates and account-generation checks prevent late responses from repopulating cleared state. A failed local cache write does not discard a valid network result.

Changed dashboard totals and metric values use the existing incoming-only 240ms fade. They never count through invented financial amounts or retain outgoing financial text; hidden-number semantics and reduced-motion handling remain with the existing shared components. Initial installations without a usable cache still use the established purchase-cost fallback until the first successful price load.

## Regression coverage

`PortfolioMarketDataTest` exercises restoration before a delayed refresh, exact purchase-cost versus market-value totals, updated quantities, TTL/explicit refresh, concurrent fetch coalescing, corrupt/unwritable storage, whitelisting, account/backend isolation, malformed/older/future boards, cancellation and late responses after account changes. Existing domain and ViewModel tests cover bond principal, pending investments and refresh/error behavior.

## Verification

Executed on 2026-11-16:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest :core:jvmTest :app:androidApp:assembleDebug :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 :app:shared:compileTestKotlinIosSimulatorArm64 --console=plain
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
python3 scripts/check-kmp-packages.py
python3 scripts/check-kmp-ui.py
python3 scripts/sync-design-colors.py --check
git diff --check
```

All final commands passed. Shared Android host: 558 tests; core JVM: 145 tests; shared iOS simulator: 584 tests; Python: 34 tests. Zero test failures/errors/skips in those suites. The first native-test invocation could not launch because the designated simulator was shut down; it executed no tests. After booting that simulator, the suite passed. Android lint retains the three previously reviewed toolchain notices. Existing compiler warnings remain visible. UI inventory/golden integrity passed; screenshot comparisons and Android device UI automation were not executed for this change.

A signed Debug iPhone build using the existing configuration also succeeded through `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 -derivedDataPath /tmp/inzira-logo-ios -allowProvisioningUpdates build` with the same JAVA_HOME. It was installed and launched on the connected iPhone. No real-account financial assertions or manual visual verification are claimed. No backend changes or commits were made.
