import { BondPdfImportError } from "./pdf-import-errors.js";
export const BNR_INVESTOR = { institution: "BNR", documentType: "bond-confirmation", format: "investor-results", version: 1 };
export const BNR_ISSUANCE = { institution: "BNR", documentType: "bond-issuance", format: "summary-market-results", version: 1 };
export const BK_PURCHASE = { institution: "BK Capital", documentType: "equity-purchase", format: "rse-purchase-contract-note", version: 1 };
export const BK_STATEMENT = { institution: "BK Capital", documentType: "statement", format: "rwf-client-statement", version: 1 };
/** Whole tokens only: never turn -100, 1,00,000, 1e6 or 12.345 into another amount.
 * Two decimal places remain exact minor units before crossing the existing numeric DTO boundary.
 */
export function pdfNumber(token, decimals = 2) {
    if (!Number.isInteger(decimals) || decimals < 1 || decimals > 6)
        return undefined;
    if (!token || !new RegExp(`^(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,${decimals}})?$`).test(token))
        return undefined;
    const plain = token.replace(/,/g, "");
    const [whole, fraction = ""] = plain.split(".");
    const units = Number(whole) * 10 ** decimals + Number(fraction.padEnd(decimals, "0"));
    const value = Number(plain);
    return Number.isSafeInteger(units) && Number.isFinite(value) && value <= 1e13 ? value : undefined;
}
/** Classification must not depend on which parser happens to be called first. */
export function assertSingleDocumentFamily(text) {
    const families = [
        /\bCLIENT\s+STATEMENT\b/i.test(text),
        /\bEQUITY\s+(?:PURCHASE|SALE)\s+CONTRACT\s+NOTE\b/i.test(text),
        /\b(?:INVESTOR\s+RESULTS|SUMMARY\s+MARKET\s+RESULTS)\b/i.test(text),
    ];
    if (families.filter(Boolean).length > 1 ||
        [...text.matchAll(/INVESTOR\s+RESULTS/gi)].length > 1 ||
        [...text.matchAll(/SUMMARY\s+MARKET\s+RESULTS/gi)].length > 1 ||
        /INVESTOR\s+RESULTS/i.test(text) && /SUMMARY\s+MARKET\s+RESULTS/i.test(text)) {
        throw new BondPdfImportError("not_bond_result");
    }
}
/** Conflicting repeated labels are ambiguous; identical page-header repeats are harmless. */
export function uniqueCapture(text, pattern) {
    const values = [...text.matchAll(new RegExp(pattern.source, "gi"))].map(match => match[1].trim());
    if (new Set(values.map(value => value.toUpperCase())).size > 1)
        throw new BondPdfImportError("not_bond_result");
    return values[0] ?? "";
}
