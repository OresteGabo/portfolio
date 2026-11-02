/** Native ESM adaptation only; financial/parser source remains the shared TypeScript. */
export function nativeModuleSource(source) {
  return source.replaceAll('"pdfjs-dist/legacy/build/pdf.mjs"', '"./pdf.mjs"')
    .replaceAll('"tesseract.js"', '"./native-tesseract.mjs"')
    .replace(/(from\s+|import\()("|')(\.\/[^"']+)\2/g,
      (_, lead, quote, target) => `${lead}${quote}${/\.(mjs|js)$/.test(target) ? target : target + '.js'}${quote}`);
}
export function nativePdfModuleOutput(output) {
  return output.replace('import { parsePurchasePdfText } from "./equity-pdf-parser.js";',
    'const parsePurchasePdfText = (text, usedOcr) => PortfolioLogic.parse(text, usedOcr);');
}
