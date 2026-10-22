import { BondPdfImportError, validateBondPdfEnvelope, validateBondPdfMetadata } from "./pdf-import-errors";

function waitForRead<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const cancel = () => reject(new BondPdfImportError("cancelled"));
    signal.addEventListener("abort", cancel, { once: true });
    promise.then(resolve, reject).finally(() => signal.removeEventListener("abort", cancel));
  });
}

function readWithFileReader(file: File, signal: AbortSignal): Promise<ArrayBuffer> {
  signal.throwIfAborted();
  if (typeof FileReader === "undefined") throw new BondPdfImportError("unreadable", { stage: "file-read", reason: "missing-browser-api" });
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    const finish = () => {
      signal.removeEventListener("abort", cancel);
      reader.onload = reader.onerror = reader.onabort = null;
    };
    const cancel = () => {
      finish();
      reader.abort();
      reject(new BondPdfImportError("cancelled"));
    };
    reader.onload = () => {
      const result = reader.result;
      finish();
      if (result instanceof ArrayBuffer) resolve(result);
      else reject(new BondPdfImportError("unreadable"));
    };
    reader.onerror = reader.onabort = () => { finish(); reject(new BondPdfImportError("unreadable", { stage: "file-read", reason: "file-unavailable" })); };
    signal.addEventListener("abort", cancel, { once: true });
    try { reader.readAsArrayBuffer(file); }
    catch { finish(); reject(new BondPdfImportError("unreadable", { stage: "file-read", reason: "file-unavailable" })); }
  });
}

/** Snapshot a selected file before clearing its input or loading PDF/OCR code. */
export async function readBondPdfBytes(file: File, signal: AbortSignal): Promise<Uint8Array> {
  validateBondPdfMetadata(file);
  signal.throwIfAborted();
  let buffer: ArrayBuffer | undefined;
  try {
    if (typeof file.arrayBuffer === "function") buffer = await waitForRead(file.arrayBuffer(), signal);
  } catch {
    if (signal.aborted) throw new BondPdfImportError("cancelled");
  }
  // Some file providers expose metadata before a complete Blob read is available.
  // Retry incomplete/failed reads once through the browser's other file API.
  if (!buffer || buffer.byteLength !== file.size) buffer = await readWithFileReader(file, signal);
  signal.throwIfAborted();
  if (buffer.byteLength !== file.size) throw new BondPdfImportError("unreadable", { stage: "file-read", reason: "file-unavailable" });
  const bytes = new Uint8Array(buffer);
  validateBondPdfEnvelope(bytes);
  return bytes;
}
