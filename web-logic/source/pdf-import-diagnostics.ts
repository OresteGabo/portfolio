import { BondPdfImportError } from "./pdf-import-errors";

const STAGES = ["metadata", "file-read", "file-envelope", "import-load", "parser-load", "document-open", "document-policy", "page-open", "page-policy", "page-text", "ocr-load", "ocr-render", "ocr-read", "page-cleanup", "purchase-detection"] as const;
const REASONS = ["validation", "file-unavailable", "missing-map-method", "missing-browser-api", "worker-version", "worker-load", "syntax", "password", "cancelled", "unknown"] as const;
const CODES = ["file_type", "file_size", "invalid_pdf", "unreadable", "password", "unsafe_content", "page_limit", "scan_limit", "text_limit", "not_bond_result", "cancelled"] as const;
const PLATFORMS = ["ios", "android", "other"] as const;
export type PdfImportStage = typeof STAGES[number];
export type PdfImportReason = typeof REASONS[number];
export type PdfImportDiagnostic = { stage: PdfImportStage; reason: PdfImportReason };
export type PdfImportReport = PdfImportDiagnostic & { code: typeof CODES[number]; platform: typeof PLATFORMS[number]; version: 1 };

// Interpret exceptions locally, then discard their text. None of these strings
// are exception messages, filenames, identifiers or extracted document contents.
export function pdfFailureReason(error: unknown): PdfImportReason {
  if (!(error instanceof Error)) return "unknown";
  if (error.name === "PasswordException") return "password";
  if (error.name === "NotReadableError" || error.name === "NotFoundError" || error.name === "SecurityError") return "file-unavailable";
  if (error.name === "SyntaxError") return "syntax";
  if (/getOrInsertComputed/.test(error.message)) return "missing-map-method";
  if (/API version[\s\S]*Worker version/i.test(error.message)) return "worker-version";
  if (/worker|dynamically imported module|module script/i.test(error.message)) return "worker-load";
  if (/is not a function|is not defined|not a constructor|unsupported/i.test(error.message)) return "missing-browser-api";
  return "unknown";
}

export function parsePdfImportReport(value: unknown): PdfImportReport | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const report = value as Record<string, unknown>;
  if (Object.keys(report).length !== 5 || report.version !== 1
    || !STAGES.some(stage => stage === report.stage) || !REASONS.some(reason => reason === report.reason)
    || !CODES.some(code => code === report.code) || !PLATFORMS.some(platform => platform === report.platform)) return null;
  return { stage: report.stage, reason: report.reason, code: report.code, platform: report.platform, version: 1 } as PdfImportReport;
}

export function makePdfImportReport(error: unknown, fallbackStage: PdfImportStage, userAgent = ""): PdfImportReport {
  const known = error instanceof BondPdfImportError;
  const platform = /iPhone|iPad|iPod/i.test(userAgent) ? "ios" : /Android/i.test(userAgent) ? "android" : "other";
  const report = parsePdfImportReport({
    stage: known && error.diagnostic ? error.diagnostic.stage : fallbackStage,
    reason: known ? error.diagnostic?.reason ?? "validation" : pdfFailureReason(error),
    code: known ? error.code : "unreadable", platform, version: 1,
  });
  return report ?? { stage: fallbackStage, reason: "unknown", code: "unreadable", platform, version: 1 };
}

export function reportPdfImportFailure(error: unknown, stage: PdfImportStage) {
  try {
    const report = makePdfImportReport(error, stage, typeof navigator === "undefined" ? "" : navigator.userAgent);
    if (report.code === "cancelled") return;
    // Reporting is optional and cannot block the dialog or change its message.
    void fetch("/api/bonds/import-diagnostics", {
      method: "POST", credentials: "same-origin", keepalive: true,
      headers: { "Content-Type": "application/json" }, body: JSON.stringify(report),
    }).catch(() => {});
  } catch { /* An unavailable reporting channel must not affect importing. */ }
}
