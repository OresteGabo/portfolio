# Financial parser fixtures

- `equity-purchase.txt`: synthetic BK-layout purchase note. Investor identity/account fields are absent; the contract/trade identifiers and amounts are test values. The two existing real RSE security identifiers are public reference data.
- `bk-statement.txt`: synthetic client code, broker references, contract identifiers and cash movements in the supported BK statement layout. It contains no customer identity.
- `bnr-investor-results-synthetic.txt`: synthetic personal-allocation result, security code and investor name with a deterministic coupon schedule. Auction size and applied/allocated principal are deliberately different.
- `bnr-market-results-2026.txt`, `bnr-market-results-pdfjs-2026.txt`, `bnr-market-results-pdfkit-2026.txt`: existing public BNR auction results represented as normalized/PDF.js/PDFKit text. They contain public auction/reference information and public signatory names, not personal investor allocations.

Regression tests mutate these texts to produce malformed, ambiguous and unsupported examples. Reader tests construct synthetic PDFs in memory; no private customer PDF is checked in. Native OCR fixtures are generated in the existing platform tests. A text fixture plus `usedOcr=true` tests parser handling of OCR output, not OCR recognition accuracy.
- `investment-reference.json`: the four supplied financial regression scenarios, with synthetic IDs and no account linkage. Maturity months do not assert exact official coupon/maturity days.
