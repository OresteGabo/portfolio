import type { PdfImportDiagnostic } from "./pdf-import-diagnostics";

export const MAX_BOND_PDF_BYTES = 10 * 1024 * 1024;

export const PDF_FILE_ERROR_MESSAGE = "❌Wrong file.";

const MESSAGES = {
  file_type: PDF_FILE_ERROR_MESSAGE,
  file_size: PDF_FILE_ERROR_MESSAGE,
  invalid_pdf: PDF_FILE_ERROR_MESSAGE,
  unreadable: PDF_FILE_ERROR_MESSAGE,
  password: PDF_FILE_ERROR_MESSAGE,
  unsafe_content: PDF_FILE_ERROR_MESSAGE,
  page_limit: PDF_FILE_ERROR_MESSAGE,
  scan_limit: PDF_FILE_ERROR_MESSAGE,
  text_limit: PDF_FILE_ERROR_MESSAGE,
  not_bond_result: PDF_FILE_ERROR_MESSAGE,
  cancelled: PDF_FILE_ERROR_MESSAGE,
} as const;

export class BondPdfImportError extends Error {
  constructor(readonly code: keyof typeof MESSAGES, readonly diagnostic?: PdfImportDiagnostic) {
    super(MESSAGES[code]);
    this.name = "BondPdfImportError";
  }
}

// Display only our fixed messages, never dependency errors or document content.
export function bondPdfImportErrorMessage(error: unknown) {
  return error instanceof BondPdfImportError && Object.hasOwn(MESSAGES, error.code)
    ? MESSAGES[error.code] : MESSAGES.unreadable;
}

export function validateBondPdfMetadata(file: Pick<File, "name" | "type" | "size">) {
  // Generic provider metadata is not a content verdict. The bytes and parsed
  // structure are still checked before extracting anything from the document.
  if (!/\.pdf$/i.test(file.name) || !["", "application/pdf", "application/octet-stream"].includes(file.type.toLowerCase())) throw new BondPdfImportError("file_type");
  if (!file.size || file.size > MAX_BOND_PDF_BYTES) throw new BondPdfImportError("file_size");
}

export function validateBondPdfEnvelope(bytes: Uint8Array) {
  const decode = new TextDecoder();
  // Some broker exports reserve a large zero-filled buffer after %%EOF.
  // Strip only PDF whitespace/NUL padding, never arbitrary trailing payloads.
  let end = bytes.length;
  while (end > 0 && [0, 9, 10, 12, 13, 32].includes(bytes[end - 1])) end--;
  if (bytes.length > MAX_BOND_PDF_BYTES ||
      !/^%PDF-(?:1\.[0-7]|2\.0)[\r\n\t ]/.test(decode.decode(bytes.subarray(0, 16))) ||
      !/%%EOF[\t\n\f\r \0]*$/.test(decode.decode(bytes.subarray(Math.max(0, end - 1024), end)))) {
    throw new BondPdfImportError("invalid_pdf", { stage: "file-envelope", reason: "validation" });
  }
}

export function hasPdfEntries(value: unknown): boolean {
  return value instanceof Map ? value.size > 0 : Boolean(value && typeof value === "object" && Object.keys(value).length);
}
