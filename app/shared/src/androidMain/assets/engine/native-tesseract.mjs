// The vendored browser ESM exposes a default object, not synthetic named exports.
import * as library from './tesseract.mjs';
const tesseract = library.default ?? library;
// AAPT expands .gz assets and packages eng.traineddata without its .gz suffix.
// Request those bundled uncompressed bytes; never fall back to a remote model.
export const createWorker = (languages, oem, options = {}, ...args) =>
  tesseract.createWorker(languages, oem, { ...options, gzip: false }, ...args);
export const PSM = tesseract.PSM;
