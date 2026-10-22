import type { EquityHoldingInput } from "./types";
import { parseEquityPurchase, validEquityDate } from "./equity-validation";
import { isRecognizedBondResult, parseBondPdfText, type BondPdfImport } from "./pdf-import-parser";
import { BondPdfImportError } from "./pdf-import-errors";

export type EquityPdfImport = { kind: "equity"; fields: EquityHoldingInput; warnings: string[]; missingFields: string[]; usedOcr: boolean };
export type PurchasePdfImport = (BondPdfImport & { kind: "bond" }) | EquityPdfImport;
export const EMPTY_EQUITY: EquityHoldingInput = {
  instrumentType: "equity", exchange: "RSE", securityName: "", ticker: "", isin: "", tradeDate: "", settlementDate: "",
  shares: 0, pricePerShare: 0, grossConsideration: 0, brokerageCommission: 0, rseTransactionFee: 0, csdTransactionLevy: 0,
  cmaTransactionFee: 0, totalCharges: 0, netAmountPayable: 0, broker: "", contractNote: "", cdsAccountNo: "", cdsReference: "", tradeReportId: "", currency: "RWF", status: "active", notes: "",
};
function date(text: string) {
  const match = text.match(/(\d{1,2})[-/\s](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/\s](\d{4})/i);
  if (!match) return "";
  const month = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"].indexOf(match[2].toLowerCase().slice(0, 3)) + 1;
  const value = `${match[3]}-${String(month).padStart(2, "0")}-${match[1].padStart(2, "0")}`;
  return validEquityDate(value) ? value : "";
}
export function parseEquityPdfText(raw: string, usedOcr = false): EquityPdfImport | null {
  const text = raw.replace(/\r/g, "");
  if ([...text.matchAll(/EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:/gi)].length !== 1) return null;
  // Sale notes and unrelated PDFs must never become a purchase.
  if (!/EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:/i.test(text) || /EQUITY\s+SALE\s+CONTRACT/i.test(text)) return null;
  const capture = (pattern: RegExp) => text.match(pattern)?.[1]?.trim() ?? "";
  const fields = { ...EMPTY_EQUITY };
  fields.contractNote = capture(/EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:\s*([A-Z0-9#/-]+)/i);
  fields.securityName = capture(/Security\s+Name\s*:\s*([^\n]+?)(?=\s+(?:Trade|Settlement|ISIN)\s|\n|$)/i);
  fields.isin = capture(/ISIN\s+Code\s*:\s*([A-Z0-9]{8,32})/i).toUpperCase();
  fields.tradeDate = date(capture(/Trade\s+Date\s*:\s*([^\n]+)/i));
  fields.settlementDate = date(capture(/Settlement\s+Date\s*:\s*([^\n]+)/i));
  fields.shares = Number(capture(/No\.?(?:\s+of)\s+Shares\s*:\s*([\d,]+)/i).replace(/,/g, ""));
  fields.pricePerShare = Number(capture(/\bPrice\s*:\s*([\d,]+(?:\.\d+)?)/i).replace(/,/g, ""));
  fields.broker = /BK\s+CAPITAL/i.test(text) ? "BK Capital" : "";
  fields.cdsAccountNo = capture(/CDS\s+Account\s+No\.\s*:\s*([A-Z0-9]+)/i);
  fields.cdsReference = capture(/CDS\s+Ref\.\s+No\.\s*:\s*([A-Z0-9]+)/i);
  fields.tradeReportId = capture(/Trade\s+Report\s+ID\s*:\s*(\d+)/i);
  const tickers: Record<string, string> = { RW000A1JCYA: "BOK", RW000A2DN989: "IMR" };
  fields.ticker = tickers[fields.isin] ?? "";
  // Each fee row may contain a percentage and an amount: take its final amount.
  const amount = (label: string) => {
    const row = text.match(new RegExp(`${label}\\s+([^\\n]+)`, "i"))?.[1];
    if (!row) return undefined;
    const values = [...row.matchAll(/\d[\d,]*\.\d{2}(?!\d|%)/g)];
    return values.length ? Number(values.at(-1)![0].replace(/,/g, "")) : undefined;
  };
  const labels = { grossConsideration: "Gross\\s+Consideration", brokerageCommission: "Brokerage\\s+Commission", rseTransactionFee: "RSE\\s+Transaction\\s+Fee", csdTransactionLevy: "CSD\\s+Transaction\\s+Levy", cmaTransactionFee: "CMA\\s+Transaction\\s+Fee", totalCharges: "Total\\s+Charges", netAmountPayable: "NET\\s+AMOUNT\\s+PAYABLE" } as const;
  const missingFields: string[] = [];
  for (const [key, label] of Object.entries(labels)) {
    const value = amount(label);
    if (value === undefined) missingFields.push(key.replace(/[A-Z]/g, (letter) => ` ${letter.toLowerCase()}`));
    else fields[key as keyof typeof labels] = value;
  }
  for (const [label, value] of [["security name", fields.securityName], ["ISIN", fields.isin], ["ticker", fields.ticker], ["trade date", fields.tradeDate], ["settlement date", fields.settlementDate], ["number of shares", fields.shares], ["share price", fields.pricePerShare]] as const) if (!value) missingFields.push(label);
  // This parser currently understands RSE purchase notes denominated in RWF only.
  if (!/All\s+figures\s+are\s+in\s+RWF/i.test(text) || !/Exchange\s*:\s*RSE\b/i.test(text)) return null;
  const warnings = usedOcr ? ["Text recognition was used. Check every number against your PDF."] : [];
  if (!missingFields.length && !parseEquityPurchase(fields)) warnings.push("The extracted totals or dates do not agree. Correct them before saving.");
  return { kind: "equity", fields, missingFields, warnings, usedOcr };
}
export function parsePurchasePdfText(text: string, usedOcr = false): PurchasePdfImport {
  const equity = parseEquityPdfText(text, usedOcr);
  const bond = parseBondPdfText(text, usedOcr);
  const isBond = isRecognizedBondResult(bond, text);
  if (equity && !isBond) return equity;
  if (isBond && !equity) return { kind: "bond", ...bond };
  throw new BondPdfImportError("not_bond_result");
}
