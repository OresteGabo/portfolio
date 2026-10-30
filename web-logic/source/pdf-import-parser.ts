import type { BondPurchaseInput } from "./types";
import { assertSingleDocumentFamily, BNR_INVESTOR, BNR_ISSUANCE, pdfNumber, uniqueCapture, type ParserIdentity } from "./document-parser-support";

export type BondPdfImport = {
  parser: ParserIdentity;
  fields: Partial<BondPurchaseInput>;
  warnings: string[];
  missingFields: string[];
  usedOcr: boolean;
};

const MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
const DATE_PATTERN = /([\dIlioO]{1,2})\s*[-/.]?\s*(Jan|Feb|Mar|Apr|May|Jun|Ju[lI]|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*[-/.]?\s*([\dIlioO]{2,4})(?!\d)/gi;
const digits = (value: string) => value.replace(/[Ili]/g, "1").replace(/[oO]/g, "0");

export function isRecognizedBondResult(result: BondPdfImport, text: string) {
  const fields = result.fields;
  const identity = Boolean(fields.bondName || fields.isin);
  const terms = [fields.tenorYears, fields.couponRate, fields.settlementDate, fields.maturityDate].filter((value) => value !== undefined && value !== "").length;
  const context = fields.issuer === "Government of Rwanda" || /INVESTOR\s+RESULTS|RESULTS\s+OF[\s\S]{0,80}TREASURY\s+BOND/i.test(text);
  return identity && terms >= 2 && context;
}

function isoDate(year: number, month: number, day: number) {
  if (year < 2000 || year > 2199 || month < 1 || month > 12 || day < 1 || day > 31) return null;
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCMonth() === month - 1 && date.getUTCDate() === day
    ? date.toISOString().slice(0, 10)
    : null;
}

function readDate(value: string) {
  value = value.trim();
  const numeric = value.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})(?=\s|$)/);
  if (numeric) return isoDate(+numeric[3], +numeric[2], +numeric[1]);
  const match = [...value.matchAll(DATE_PATTERN)][0];
  if (!match || match.index !== 0) return null;
  const year = +digits(match[3]);
  return isoDate(year < 100 ? 2000 + year : year, MONTHS.indexOf(match[2].toLowerCase().slice(0, 3).replace(/^jui$/, "jul")) + 1, +digits(match[1]));
}

function number(value: string | undefined) {
  if (!value) return undefined;
  const token = value.replace(/\s/g, "");
  const decimals = Math.max(2, (token.split(".")[1] ?? "").length);
  return decimals <= 6 ? pdfNumber(token, decimals) : undefined;
}

/** BNR result keywords only. Text is data; nothing in a document is executed. */
export function parseBondPdfText(raw: string, usedOcr = false): BondPdfImport {
  assertSingleDocumentFamily(raw);
  const text = raw.replace(/\r/g, "").replace(/[|_]/g, " ");
  const fields: Partial<BondPurchaseInput> = {};
  const warnings: string[] = [];
  const capture = (pattern: RegExp) => uniqueCapture(text, pattern) || undefined;
  const name = capture(/(?:ISSUANCE\s+NUMBER|ISSUE\s+NUMBER)\s*[:\s]*([A-Z]+\d+\s*\/\s*\d{4}\s*\/\s*\d+\s*(?:YR|Y))/i);
  if (name) fields.bondName = name.replace(/\s/g, "").toUpperCase();
  const code = capture(/(?:BOND\s+CODE|ISIN(?:\s*\/\s*SECURITY\s+CODE)?)\s*[:\s]*([A-Z]{2}[A-Z0-9/.-]{8,30})/i);
  if (code) fields.isin = code.toUpperCase();
  if (/NATIONAL BANK OF RWANDA|GOVERNMENT OF RWANDA|BANKI NKURU/i.test(text)) {
    fields.issuer = "Government of Rwanda";
    fields.instrumentType = "treasury";
    fields.currency = "RWF";
    fields.market = "primary";
  }
  const tenor = number(capture(/TENOR\s*[:\s]*(\S+?)\s*Y(?:ears)?(?=\s|$)/i) ?? capture(/RESULTS\s+OF\s+(\d+)\s+YEAR/i));
  if (tenor && Number.isInteger(tenor) && tenor <= 100) fields.tenorYears = tenor;
  const price = number(capture(/(?:^|\n)\s*(?:EXECUTED\s+)?PRICE\s*[:\s]*(\S+)/i));
  if (price && price <= 1000) fields.pricePercent = price;
  const rate = number(capture(/C[ao]upon\s+(?:Interest\s+)?rate\s*[:\s]*([^\s%]+)\s*%(?=\s|$)/i));
  if (rate !== undefined && rate <= 100) fields.couponRate = rate / 100;

  const settlement = capture(/SETTLEMENT\s+DATE[ \t:]*\n?[ \t]*([\dIlioO][^\n]*)/i);
  const maturity = capture(/REDEMP?E?TION\s+DATE[ \t:]*\n?[ \t]*([\dIlioO][^\n]*)/i)
    ?? capture(/MATURITY\s+DATE[ \t:]*\n?[ \t]*([\dIlioO][^\n]*)/i);
  if (settlement) {
    const date = readDate(settlement);
    if (date) {
      fields.settlementDate = date;
      fields.purchaseDate = date;
      warnings.push("Trade date defaults to settlement date. Change it if your transaction used a different date.");
    }
  }
  if (maturity) {
    const date = readDate(maturity);
    if (date) fields.maturityDate = date;
  }

  // AMOUNT near the bond header is the whole issuance, never this investor's principal.
  // In the BNR investor table the columns are applied, allocated, settlement, coupon.
  const investorSection = text.split(/INVESTOR\s+RESULTS/i)[1]?.split(/Digitally\s+signed/i)[0];
  if (investorSection && /Allocation/i.test(investorSection) && /Coupon\s+Am[o0]unt/i.test(investorSection)) {
    const amounts = investorSection.split(/\s+/).filter(token => /^[+-]?\d/.test(token)).map(token => pdfNumber(token));
    if (!/[+-]\s+\d/.test(investorSection) && amounts.length === 4 && amounts.every(value => value !== undefined) && amounts[1]! > 0) {
      fields.faceValue = amounts[1];
      if (fields.pricePercent) {
        const expected = Math.round(amounts[1]! * fields.pricePercent) / 100;
        if (Math.abs(expected - amounts[2]!) > 1) {
          warnings.push("Settlement amount differs from principal × price. Enter any accrued interest and fees before saving.");
        }
      }
    } else {
      warnings.push("Could not identify one investor allocation unambiguously. Enter your own face value; the total issuance amount was ignored.");
    }
  }
  if (/BK\s+CAPITAL/i.test(investorSection ?? "")) fields.broker = "BK Capital";

  const schedule = text.match(/COUPON\s+PAYMENT(?:\s+DATES)?([\s\S]*?)(?=SETTLEMENT\s+DATE|REDEMP?TION\s+DATE|MATURITY\s+DATE|INVESTOR\s+RESULTS|$)/i)?.[1];
  let invalidDates = 0;
  if (schedule) {
    const dates = [...schedule.matchAll(DATE_PATTERN)].flatMap((match) => {
      const date = readDate(match[0]);
      if (!date) invalidDates++;
      return date ? [date] : [];
    });
    const unique = [...new Set(dates)].sort();
    if (unique.length !== dates.length) invalidDates++;
    if (unique.length) {
      const filtered = unique.filter((date) => (!fields.settlementDate || date > fields.settlementDate) && (!fields.maturityDate || date <= fields.maturityDate));
      invalidDates += unique.length - filtered.length;
      fields.couponDates = filtered;
      fields.firstCouponDate = filtered[0] ?? "";
      fields.couponFrequency = 2;
      // OCR results always require review. Missing dates are never filled by guessing.
      fields.scheduleConfidence = !usedOcr && !invalidDates && fields.tenorYears && filtered.length === fields.tenorYears * 2 ? "confirmed" : "estimated";
      if (fields.tenorYears && filtered.length !== fields.tenorYears * 2) {
        warnings.push(`Found ${filtered.length} coupon dates; a full ${fields.tenorYears}-year semiannual schedule usually has ${fields.tenorYears * 2}. Check for missing dates or a reopened bond.`);
      }
    }
  }
  if (invalidDates) warnings.push("Some coupon dates were invalid, duplicated, or outside the bond term. Check the schedule against your PDF.");
  if (usedOcr) warnings.push("This PDF needed text recognition. Check every value, especially the security code, principal, and coupon dates, against the original.");

  const required = [
    ["bondName", "Bond name"], ["isin", "Security code"], ["tenorYears", "Tenor"],
    ["faceValue", "Your allocated principal"], ["pricePercent", "Executed price"],
    ["couponRate", "Coupon rate"], ["settlementDate", "Settlement date"],
    ["maturityDate", "Maturity date"], ["firstCouponDate", "Coupon schedule"],
  ] as const;
  const missingFields = required.filter(([key]) => fields[key] === undefined || fields[key] === "").map(([, label]) => label);
  return { parser: BNR_INVESTOR, fields, warnings, missingFields, usedOcr };
}


export type BondIssuanceImport = {
  kind: "bond-issuance";
  parser: ParserIdentity;
  fields: Partial<BondPurchaseInput> & { issuanceDate?: string; amountOffered?: number; appliedAmount?: number; amountSold?: number; bidCount?: number; yieldRate?: number };
  warnings: string[]; missingFields: string[]; usedOcr: boolean;
};

/** Public auction results have no investor allocation and must never become a purchase. */
export function parseBondIssuancePdfText(raw: string, usedOcr = false): BondIssuanceImport | null {
  assertSingleDocumentFamily(raw);
  if (!/SUMMARY\s+MARKET\s+RESULTS/i.test(raw) || /INVESTOR\s+RESULTS/i.test(raw)) return null;
  // PDF glyph runs sometimes insert spaces inside identifiers and years. Restrict repairs to labels.
  const text = raw.replace(/((?:ISSUANCE|ISSUE)\s+NUMBER|BOND\s+CODE|SETTLEMENT\s+DATE|REDEMP?E?TION\s+DATE|MATURITY\s+DATE|Coupon\s+(?:Interest\s+)?rate|Y\s*ield\s+rate)([^\n]+)/gi,
    (_, label, value) => label + " " + value.replace(/\s/g, ""))
    .replace(/((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s*-\s*)(\d)\s+(\d)\b/gi, "$1$2$3");
  const base = parseBondPdfText(text, usedOcr);
  if (!isRecognizedBondResult(base, text)) return null;
  const fields: BondIssuanceImport["fields"] = { ...base.fields };
  delete fields.purchaseDate; delete fields.faceValue; delete fields.broker;
  const summary = text.split(/SUMMARY\s+MARKET\s+RESULTS/i)[1] ?? "";
  const issueDate = summary.match(/\b(\d(?:\s*\d)?)\s*[-/]\s*(\d(?:\s*\d)?)\s*[-/]\s*(\d(?:\s*\d){3})\b/);
  if (issueDate) fields.issuanceDate = isoDate(number(issueDate[3])!, number(issueDate[2])!, number(issueDate[1])!) ?? undefined;
  const amountMatches = [...summary.matchAll(/(?<![\d,.\-])\b\d(?:\s*\d){0,2}(?:\s*,\s*\d\s*\d\s*\d){2,}(?:\s*\.\s*\d+)?\b(?![\d,.])/g)];
  const totals = amountMatches.map(m => pdfNumber(m[0].replace(/\s/g, "")));
  if (!/[+-]\s+\d[\d\s]*,/.test(summary) && totals.length === 3 && totals.every(value => value !== undefined)) [fields.amountOffered, fields.appliedAmount, fields.amountSold] = totals;
  const lastAmount = amountMatches.at(-1);
  const bids = lastAmount && summary.slice(lastAmount.index! + lastAmount[0].length).match(/^\s+(\d+)\s+[\d\s.]+%/);
  if (bids) fields.bidCount = +bids[1];
  const yieldRate = number(uniqueCapture(text, /Y\s*ield\s+rate\s*[:\s]*([^\s%]+)\s*%(?=\s|$)/i));
  if (yieldRate !== undefined && yieldRate <= 100) fields.yieldRate = yieldRate / 100;
  const required = ["bondName", "isin", "tenorYears", "pricePercent", "couponRate", "settlementDate", "maturityDate", "firstCouponDate", "issuanceDate", "amountOffered", "appliedAmount", "amountSold", "bidCount", "yieldRate"] as const;
  return { kind: "bond-issuance", parser: BNR_ISSUANCE, fields, usedOcr,
    warnings: base.warnings.filter(w => !w.startsWith("Trade date defaults")),
    missingFields: required.filter(k => fields[k] === undefined || fields[k] === "") };
}
