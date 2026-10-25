# Apple Passwords integration

Apple Passwords AutoFill and the app's optional secure sign-in vault have separate responsibilities. Passwords stores the website username/password for filling login forms across the user's devices; the app vault protects its own remembered sign-in and session with device authentication. Logging out removes only the app-vault entry.

## App configuration

- `app/iosApp/iosApp/Portfolio.entitlements` associates `webcredentials:bonds.orestegabo.dev`. Website password sharing is **off by default** so a Personal Team provisioning profile can build/run the app. Both Debug and Release select that entitlement file only when `GABO_PASSWORD_SHARING_ENABLED=YES` in `app/iosApp/Configuration/Config.xcconfig` (or a build-setting override). The same flag is embedded in Info.plist, and native code checks it before offering website-password saving.
- The bundle identifier is stable across signing configurations (it no longer appends `TEAM_ID`). The configured Debug signing team is `X5HS2K2N42`, and the current bundle ID is `dev.orestegabo.portfolio.Portfolio`. The website app identity is therefore `X5HS2K2N42.dev.orestegabo.portfolio.Portfolio`. If the signing team, App ID prefix or resolved bundle ID changes, update the website association to the signed app's actual `application-identifier`.
- To enable website password sharing, first select a signing team/profile that supports Associated Domains, publish the matching website association, then set `GABO_PASSWORD_SHARING_ENABLED = YES`. The locally available Personal Team profile does not include that capability. Leave the flag at `NO` for that profile. The existing Release team selection remains controlled by `TEAM_ID` in the project configuration.
- Native UIKit input traits identify the email as the username and distinguish login passwords from registration passwords. Shared Compose semantics still provide Android AutoFill hints.
- When website password sharing is enabled and the server accepts manual login/registration, the iOS app offers **Save in Passwords?** and, on consent, calls Apple's shared-web-credential API. The API supports the app's iOS 18.2 deployment target. Saving is restricted to the exact production HTTPS origin; test/custom-server credentials are never saved under the production website's identity.
- The app's own secure sign-in switch remains independent. Failed/rejected logins never offer password saving; a declined or unsuccessful save does not reject successful authentication. No password or raw system error is logged or displayed.

## Website configuration

Prepared in `/Users/muhirwagabooreste/WebstormProjects/gabo`:

- `app/.well-known/apple-app-site-association/route.ts` serves a JSON `webcredentials.apps` list containing the exact app identity above.
- `proxy.ts` excludes that endpoint from the bonds subdomain rewrite. Apple must receive JSON directly, without a redirect or sign-in page.

Verified on 25 October 2026: the public association endpoint returns **HTTP 200** with `X5HS2K2N42.dev.orestegabo.portfolio.Portfolio` in `webcredentials.apps`. No further website publication is needed for that app identity. The current device provisioning profile does **not** permit Associated Domains, and `GABO_PASSWORD_SHARING_ENABLED` remains `NO`. A capable signing profile is still required for automatic matching with website passwords. The app retains native username/password input traits so users can use the password-provider options available on their device.

After publication, verify:

```sh
curl -i https://bonds.orestegabo.dev/.well-known/apple-app-site-association
```

Expect HTTP 200, `Content-Type: application/json`, and:

```json
{"webcredentials":{"apps":["X5HS2K2N42.dev.orestegabo.portfolio.Portfolio"]}}
```

Build/install the app with the matching Associated Domains signing profile after the endpoint is available. Apple validates association when installing and may cache domain associations. On the iPhone, enable Password AutoFill and the desired provider in Settings, then manually sign in and accept Save password. Sign out and focus the username/password fields to check AutoFill. Actual password storage/sync requires testing on the owner's device and account; no real credentials were used during development.

## Verification

Shared regressions verify that accepted credentials invoke the password manager once, rejected credentials never do, password-manager errors do not reject login or expose their raw message, and custom/HTTP/lookalike origins cannot share a production password entry. Local web checks verify the JSON response, app identity, no rewrite/redirect on the association URL, and preservation of normal portfolio routing. Web TypeScript checking also passes.

References: [Apple Password AutoFill](https://developer.apple.com/documentation/security/password-autofill), [app access to saved passwords](https://support.apple.com/en-my/guide/security/sec8762eb992/web), [managing shared credentials](https://developer.apple.com/documentation/security/managing-shared-credentials).
