# BNR market results imports

Documents → Upload PDF and Add purchase → Choose PDF recognize BNR Summary Market Results separately from investor confirmations. Importing reads locally; only the review screen’s **Save bond issuance** action writes to the API. The review explicitly states that this public PDF and its extracted bond details are shared with invited users. It never creates an investor purchase or treats auction-wide amounts as a personal allocation.

The parser preserves the security code, issuance name/date, settlement and maturity dates, tenor, price, coupon/yield rates, auction offered/applied/sold amounts, bid count, and explicit coupon schedule. Regression fixtures include the supplied October 2026 report’s extracted text and Android PDF.js glyph layout. Documents display all saved facts and coupon dates. Calendar entries merge by settlement/maturity to preserve subscription dates; pending applications carry the known security code and retain submitted status until an investor confirmation arrives.

## Backend

The separate Spring project is `/Users/muhirwagabooreste/AndroidStudioProjects/gabo-portfolio-api`. The existing authenticated `POST /api/bonds/documents` chunk protocol now accepts `category: "Bond issuance"` and a structured `metadata.issuance` object. Server validation allows only public issuance fields. Completed public reports use the shared `bond_issuance_documents` table; investor confirmations continue to use owner-scoped `bond_uploaded_documents`.

Flyway V4 creates the shared table. V3 retains the prior per-account identity guard, though new public reports go into V4’s shared catalog. Deploy/restart the updated Spring backend with migrations enabled before using this feature. The default production origin is unchanged, and the separate legacy Next.js backend has not been updated.

A unique canonical identity combines the normalized security code and settlement date, allowing reopenings. The database resolves competing uploads across accounts atomically. Repeated imports with identical validated terms return the existing `documentId` and `duplicate: true`, even if PDF bytes differ. Conflicting terms return HTTP 409; the original terms and source remain unchanged. The PDF content hash also has a unique constraint. Completed temporary chunks are deleted, and abandoned uploads expire through the existing upload cleanup. Authentication is required to list and download public reports; private documents retain account isolation.

## Verification

JavaScript tests cover report classification, complete extraction, PDF.js spacing, OCR review, incomplete data, ambiguous investor documents, and native bundle parity. Shared Kotlin tests cover review validation, preventing conversion into a purchase, calendar merging, and pending security codes. Spring PostgreSQL integration tests cover concurrent cross-account reuploads, unique stored reports, conflict rejection, reopenings, shared access, authentication, and invalid figures. Android assembles, shared host tests pass, and the shared iPhone target compiles. No production records were changed or app installation performed.
