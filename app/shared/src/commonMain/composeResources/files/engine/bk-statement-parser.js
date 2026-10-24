import { validEquityDate } from "./equity-validation.js";
import { BondPdfImportError } from "./pdf-import-errors.js";
const value = (text) => Number(text.replace(/,/g, ""));
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
    if (!/\bBK\s+CAPITAL\b/i.test(raw) || !/\bCLIENT\s+STATEMENT\b/i.test(raw))
        return null;
    const fail = () => { throw new BondPdfImportError("not_bond_result"); };
    if (!/Balance\s*\(RWF\)/i.test(raw) || /\b(?:USD|EUR|GBP)\b/.test(raw))
        return fail();
    const period = (label) => date(raw.match(new RegExp(`${label}\\s+Date\\s*:\\s*(\\d{1,2}-[a-z]{3}-\\d{4})`, "i"))?.[1] ?? "");
    const fromDate = period("From"), toDate = period("To");
    if (!fromDate || !toDate || toDate < fromDate)
        return fail();
    const ledger = raw.split(/Opening\s+Balance\s+As\s+On/i)[1]?.split(/Closing\s+Balance\s+As\s+On/i)[0];
    if (!ledger)
        return fail();
    const starts = [...ledger.matchAll(/^\s*(\d{1,2}-[a-z]{3}-\d{4})\s+(Receipt|Journal|Purchase|Sale|Payment)\b/gim)];
    const rows = [];
    for (let i = 0; i < starts.length; i++) {
        const match = starts[i];
        const block = ledger.slice(match.index, starts[i + 1]?.index);
        const text = block.replace(/\s+/g, " ").trim();
        const day = date(match[1]);
        if (!day || day < fromDate || day > toDate)
            return fail();
        // Receipts, fees, sales and transfers never become investment purchases.
        if (match[2].toLowerCase() !== "purchase" && !(match[2].toLowerCase() === "journal" && /\bBond\s+Cost\b/i.test(text)))
            continue;
        const amounts = [...text.matchAll(/-?\b\d[\d,]*\.\d{2}\b/g)].map(item => value(item[0]));
        if (amounts.length < 2)
            return fail();
        const amount = amounts[amounts.length - 2], credit = amounts[amounts.length - 1];
        if (!Number.isFinite(amount) || amount <= 0 || amount > 1e13 || credit !== 0)
            return fail();
        if (match[2].toLowerCase() === "purchase") {
            const trade = text.match(/Purchase\s+of\s+([A-Z][A-Z0-9]{1,9})\s+([\d,]+)\s*@\s*([\d,]+\.\d{2})\s+on\s+(\d{2})-(\d{2})-(\d{4})/i);
            const contract = block.match(/\bPurchase\s+(\d+#)(?:[ \t]*(\d+)\b|[^\n]*\n\s*(\d+)\b)/i);
            if (!trade || !contract)
                return fail();
            const shares = value(trade[2]), pricePerShare = value(trade[3]);
            if (!Number.isSafeInteger(shares) || shares <= 0 || pricePerShare <= 0 || `${trade[6]}-${trade[5]}-${trade[4]}` !== day || amount < shares * pricePerShare)
                return fail();
            rows.push({ kind: "equity", date: day, amount, title: trade[1].toUpperCase(), ticker: trade[1].toUpperCase(), shares, pricePerShare, contractNote: contract[1] + (contract[2] ?? contract[3]) });
        }
        else {
            const title = text.match(/\b([A-Za-z]+\s+Bond\s+Cost)\b/i)?.[1] ?? "Bond purchase";
            rows.push({ kind: "bond", date: day, amount, title });
        }
    }
    if (!rows.length || rows.length > 500)
        return fail();
    return { kind: "statement", broker: "BK Capital", currency: "RWF", fromDate, toDate, rows, usedOcr };
}
