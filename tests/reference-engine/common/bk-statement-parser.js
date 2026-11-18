import { validEquityDate } from "./equity-validation.js";
import { BondPdfImportError } from "./pdf-import-errors.js";
import { assertSingleDocumentFamily, BK_STATEMENT, pdfNumber, uniqueCapture } from "./document-parser-support.js";
function date(raw) {
    const match = raw.match(/^(\d{1,2})-(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)-(\d{4})$/i);
    if (!match)
        return "";
    const month = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"].indexOf(match[2].toLowerCase()) + 1;
    const result = `${match[3]}-${String(month).padStart(2, "0")}-${match[1].padStart(2, "0")}`;
    return validEquityDate(result) ? result : "";
}
/** A statement is a ledger for reconciliation, not evidence of missing execution terms. */
export function parseBkStatement(raw, usedOcr = false) {
    assertSingleDocumentFamily(raw);
    if (!/\bBK\s+CAPITAL\b/i.test(raw) || !/\bCLIENT\s+STATEMENT\b/i.test(raw))
        return null;
    const fail = () => { throw new BondPdfImportError("not_bond_result"); };
    if (!/Balance\s*\(RWF\)/i.test(raw) || /\b(?:USD|EUR|GBP)\b/.test(raw))
        return fail();
    const period = (label) => date(uniqueCapture(raw, new RegExp(`${label}\\s+Date\\s*:[ \\t]*(\\S+)`, "i")));
    const fromDate = period("From"), toDate = period("To");
    if (!fromDate || !toDate || toDate < fromDate)
        return fail();
    if ([...raw.matchAll(/Opening\s+Balance\s+As\s+On/gi)].length !== 1 ||
        [...raw.matchAll(/Closing\s+Balance\s+As\s+On/gi)].length !== 1 ||
        raw.search(/Closing\s+Balance\s+As\s+On/i) < raw.search(/Opening\s+Balance\s+As\s+On/i))
        return fail();
    const ledger = raw.split(/Opening\s+Balance\s+As\s+On/i)[1]?.split(/Closing\s+Balance\s+As\s+On/i)[0];
    if (!ledger)
        return fail();
    const starts = [...ledger.matchAll(/^\s*(\S+-\S+)\s+([A-Za-z][A-Za-z-]*)\b/gim)];
    const rows = [];
    const movements = [];
    for (let i = 0; i < starts.length; i++) {
        const match = starts[i];
        const block = ledger.slice(match.index, starts[i + 1]?.index);
        const text = block.replace(/\s+/g, " ").trim();
        const day = date(match[1]);
        if (!day || day < fromDate || day > toDate)
            return fail();
        // PDFKit and PDF.js wrap descriptions/references after the amount columns.
        // Require one complete pair on a physical line, never choose arbitrary numbers
        // from the flattened narrative. Signs in broker references are not amounts.
        const columns = block.split(/\r?\n/).map(line => line.trim().split(/\s+/))
            .filter(tokens => tokens.length >= 2 && tokens.slice(-2).every(token => /^[+\-\d.,()]+$/.test(token)));
        if (columns.length !== 1)
            return fail();
        const tokens = columns[0];
        if (/^[+-]$/.test(tokens.at(-3) ?? ""))
            return fail();
        const amount = pdfNumber(tokens.at(-2)), credit = pdfNumber(tokens.at(-1));
        if (amount === undefined || credit === undefined)
            return fail();
        const purchase = match[2].toLowerCase() === "purchase" || match[2].toLowerCase() === "journal" && /\bBond\s+Cost\b/i.test(text);
        if (!purchase) {
            if (amount > 0 && credit > 0)
                return fail();
            if (amount === 0 && credit === 0)
                continue;
            const kind = /\bdividend\b/i.test(text) ? "dividend" : /\bcoupon\b/i.test(text) ? "coupon" :
                /\b(?:fee|fees|charge|charges|custody|commission|levy)\b/i.test(text) ? "fee" : match[2].toLowerCase() === "receipt" ? "cash-receipt" : "other";
            movements.push({ kind, date: day, amount: amount || credit, direction: amount ? "debit" : "credit", rowNumber: i + 1,
                title: kind === "cash-receipt" ? "Cash receipt" : kind === "fee" ? "Statement fee" : kind === "coupon" ? "Coupon movement" : kind === "dividend" ? "Dividend movement" : "Other statement movement" });
            continue;
        }
        if (!Number.isFinite(amount) || amount <= 0 || amount > 1e13 || credit !== 0)
            return fail();
        if (match[2].toLowerCase() === "purchase") {
            const trade = text.match(/Purchase\s+of\s+([A-Z][A-Z0-9]{1,9})\s+(\S+)\s*@\s*(\S+)\s+on\s+(\d{2})-(\d{2})-(\d{4})/i);
            // An inline quantity is not the contract suffix. A suffix is attached to
            // '#' or starts its own continuation line before the broker/narrative.
            const contract = block.match(/\bPurchase[ \t]+(\d+#)(?:([0-9]+)\b|[ \t]+(\d+)(?=[ \t]+Purchase\b|[ \t]*(?:\r?\n|$))|[^\n]*\n[ \t]*(\d+)(?=[ \t]+(?:by\b|[A-Za-z][A-Za-z0-9]*[ \t]*-)|[ \t]*(?:\r?\n|$)))/i);
            if (!trade || !contract)
                return fail();
            const shares = pdfNumber(trade[2]), pricePerShare = pdfNumber(trade[3]);
            if (shares === undefined || pricePerShare === undefined)
                return fail();
            if (!Number.isSafeInteger(shares) || shares <= 0 || pricePerShare <= 0 || `${trade[6]}-${trade[5]}-${trade[4]}` !== day || amount < shares * pricePerShare)
                return fail();
            rows.push({ kind: "equity", rowNumber: i + 1, date: day, amount, title: trade[1].toUpperCase(), ticker: trade[1].toUpperCase(), shares, pricePerShare, contractNote: contract[1] + (contract[2] ?? contract[3] ?? contract[4]) });
        }
        else {
            const title = text.match(/\b([A-Za-z]+\s+Bond\s+Cost)\b/i)?.[1] ?? "Bond purchase";
            rows.push({ kind: "bond", rowNumber: i + 1, date: day, amount, title });
        }
    }
    if (!rows.length && !movements.length || rows.length + movements.length > 500)
        return fail();
    return { kind: "statement", parser: BK_STATEMENT, broker: "BK Capital", currency: "RWF", fromDate, toDate, rows, movements, usedOcr };
}
