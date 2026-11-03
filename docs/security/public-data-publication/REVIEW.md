# Restrict shared reference-data publication

Prepared 3 November 2026. Backend base revision is recorded in `BASE_REVISION`.

## Current status

The mobile working tree has capability checks, blocked ordinary-user publication, owner-only grant/revoke controls (shown only when the API advertises the capability), and updated English/French privacy copy. **The actual companion API checkout is unchanged. No production deployment or live database operation was performed.**

Automatic approval review rejected applying the companion API changes because the authorization and legacy-source migration could affect existing shared records beyond its interpretation of the approved scope. `api-hardening.patch` is the complete, tested proposed backend change, prepared in `/private/tmp/struct-public-data-api-review`. Applying it to the companion checkout still requires user approval. Applying source changes is separate from deploying the backend or running its migration against production.

Until the backend patch is applied and deployed, client checks alone do not close the API vulnerability. Coordinate the mobile/privacy release with the API rollout.

## Confirmed paths and threat review

- DocumentService currently permits any authenticated account to publish an issuance by setting `visibility=shared`. Successful publication creates shared sources/instruments, refreshes other accounts' pending terms and queues notifications. Consent and PDF shape validation are not authority or source verification.
- Private confirmation saves currently contribute caller-supplied coupon facts to a shared aggregate. A file being owned by the caller does not establish financial authenticity. Disabling only issuance upload would leave this second path open.
- Calendar data is bundled in source, not replaced by an ordinary user-upload endpoint found in this review. Mobile calendar overlays consume issuance documents; those must come from the trusted shared read path.

The abuse cases are an invited account poisoning shared financial reference data, forging owner/publisher metadata, resuming after capability revocation, replaying a duplicate upload, self-granting or granting another account a role, and consuming previously untrusted sources after a write-only fix.

## Proposed backend policy

- The server always permits the owner account. All other accounts default to no publication permission.
- A named account gets the narrow `canPublishPublicData` capability only through the owner's authenticated grant/revoke endpoint. Staff do not become owners and cannot access owner account administration or grant permissions themselves.
- Server-owned capability checks run on every issuance chunk, including final chunks, duplicates and resumed uploads. They do not use client role/visibility/publisher fields. Grant/revoke changes and uploads use the same account transaction lock.
- Grant/revoke events retain the owner actor, target account, action and time in a private audit table. Account deletion removes active publication permission.
- Private confirmations no longer populate any shared reference pool, including when uploaded by staff or owner. Old private-derived aggregates are not read as reference terms. Curated bundled instruments and approved public issuances remain sources.
- V12 adds approval state and grants no staff access automatically. Existing issuance sources are approved only when their stored, server-authored `_publishedBy` identifies the owner. Unknown and ordinary-contributor sources remain preserved but quarantined, including their derived instrument read paths.
- Shared library/download, pending enrichment, evidence matching and coupon-reference reads exclude quarantined sources. Authorized publishers can release a quarantined identity only by completing a reviewed PDF re-upload. Source replacement cannot overwrite a distinct curated instrument.
- Revoking a staff member blocks future publication, including an unfinished upload. It does not invalidate public documents they legitimately published while authorized.

## Data and rollout implications

The migration preserves source files and financial purchase snapshots. It does not invent corrections to amounts, rates or dates already copied into historical/pending records by earlier publication. Those records and existing notifications require an owner-led review against original evidence. Restart/refresh clients after rollout so old in-memory shared reference values are replaced.

Owner files from before publisher provenance was recorded can also be quarantined. This is intentional fail-closed handling: re-upload the checked official report rather than assume unknown provenance is trusted. Review this effect before deploying V12. No production records were inspected or changed in this task.

An authorized person can still publish mistaken information; role restriction is not independent verification by BNR. Publishers must check the original official source and extracted values before publishing. A future calendar upload or other shared-data mutation must use the same server capability and provenance policy.

## Validation

- The isolated backend patch passed **73 tests**, including **47 Spring API integration tests**, against a newly initialized disposable loopback PostgreSQL cluster. No production credentials or data were used. Clusters were stopped after testing.
- Regressions cover ordinary-user denial despite forged sharing/owner fields; owner grant and revoke; staff inability to administer or delegate; duplicate and interrupted-upload denial after revocation; quarantined reads/enrichment; authorized re-publication; private PDF isolation and exclusion of old private aggregates.
- Backend Kotlin/test compilation and bootJar packaging passed in the isolated copy.
- Mobile shared host suite: **357 passed**. Android debug/test APK assembly and shared iOS simulator compilation passed. Existing issuance/duplicate fixtures now explicitly identify their authorized publisher; a new negative flow proves ordinary users cannot upload or confirm through the publication workflow even with consent.
- `git apply --check` succeeds against the recorded companion revision. It was used only to validate the patch; the companion checkout remains unchanged.
- No production tests, deployment, live migrations, real staff grants, VoiceOver/TalkBack run or iOS visual inspection were performed.

Reference: OWASP Authorization Cheat Sheet recommends least privilege, default deny and server-side checks on every request: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
