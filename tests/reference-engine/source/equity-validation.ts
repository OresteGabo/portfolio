import type { EquityHoldingInput } from "./types";

export function validEquityDate(value: unknown): value is string {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}

export function parseEquityPurchase(value: unknown): EquityHoldingInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const text = (key: string, max: number, required = false) => {
    const value = raw[key] ?? "";
    return typeof value === "string" && value.trim().length <= max && (!required || value.trim()) ? value.trim() : null;
  };
  const securityName = text("securityName", 160, true), isin = text("isin", 32, true), ticker = text("ticker", 10, true);
  const broker = text("broker", 120), contractNote = text("contractNote", 120), cdsAccountNo = text("cdsAccountNo", 120), cdsReference = text("cdsReference", 120), tradeReportId = text("tradeReportId", 120);
  if (!securityName || !isin || !ticker || !/^[A-Z][A-Z0-9]{1,9}$/.test(ticker) || !/^[A-Z0-9]{8,32}$/.test(isin)
    || [broker, contractNote, cdsAccountNo, cdsReference, tradeReportId].includes(null)
    || raw.exchange !== "RSE" || raw.currency !== "RWF" || raw.instrumentType !== "equity"
    || !validEquityDate(raw.tradeDate) || !validEquityDate(raw.settlementDate) || raw.settlementDate < raw.tradeDate) return null;
  const numericKeys = ["shares", "pricePerShare", "grossConsideration", "brokerageCommission", "rseTransactionFee", "csdTransactionLevy", "cmaTransactionFee", "totalCharges", "netAmountPayable"] as const;
  const numbers = {} as Record<typeof numericKeys[number], number>;
  for (const key of numericKeys) {
    const number = raw[key];
    if (typeof number !== "number" || !Number.isFinite(number) || number < 0 || number > 1e13) return null;
    numbers[key] = number;
  }
  const rounded = (n: number) => Math.round(n * 100) / 100;
  if (!Number.isSafeInteger(numbers.shares) || numbers.shares <= 0 || numbers.pricePerShare <= 0
    || Math.abs(rounded(numbers.shares * numbers.pricePerShare) - numbers.grossConsideration) > 0.02
    || Math.abs(rounded(numbers.brokerageCommission + numbers.rseTransactionFee + numbers.csdTransactionLevy + numbers.cmaTransactionFee) - numbers.totalCharges) > 0.02
    || Math.abs(rounded(numbers.grossConsideration + numbers.totalCharges) - numbers.netAmountPayable) > 0.02) return null;
  return { instrumentType: "equity", securityName, isin, ticker, exchange: "RSE", currency: "RWF", tradeDate: raw.tradeDate, settlementDate: raw.settlementDate,
    ...numbers, broker: broker!, contractNote: contractNote!, cdsAccountNo: cdsAccountNo!, cdsReference: cdsReference!, tradeReportId: tradeReportId!, status: "active", notes: "" };
}
