/** Copy only public, client-side investment logic. No private data or environment files. */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const web = process.argv[2] || '/Users/muhirwagabooreste/WebstormProjects/gabo';
const require = createRequire(path.join(web, 'package.json'));
const ts = require('typescript');
const root = path.resolve(import.meta.dirname, '..');
const assets = path.join(root, 'app/androidApp/src/main/assets/engine');
fs.mkdirSync(assets, { recursive: true });
const modules = ['calculations', 'pdf-import', 'pdf-import-parser', 'pdf-import-errors', 'pdf-import-diagnostics', 'pdf-file', 'equity-pdf-parser', 'equity-validation', 'types'];
for (const name of modules) {
  const original = fs.readFileSync(path.join(web, 'lib/bonds', `${name}.ts`), 'utf8');
  fs.writeFileSync(path.join(root, 'web-logic/source', `${name}.ts`), original);
  let code = original.replaceAll('"pdfjs-dist/legacy/build/pdf.mjs"', '"./pdf.mjs"').replaceAll('"tesseract.js"', '"./tesseract.mjs"');
  code = code.replace(/(from\s+|import\()("|')(\.\/[^"']+)\2/g, (_, lead, quote, target) => `${lead}${quote}${/\.(mjs|js)$/.test(target) ? target : target + '.js'}${quote}`);
  const result = ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
  fs.writeFileSync(path.join(assets, `${name}.js`), result.outputText);
}
fs.copyFileSync(path.join(web, 'node_modules/pdfjs-dist/legacy/build/pdf.mjs'), path.join(assets, 'pdf.mjs'));
fs.copyFileSync(path.join(web, 'node_modules/tesseract.js/dist/tesseract.esm.min.js'), path.join(assets, 'tesseract.mjs'));
fs.cpSync(path.join(web, 'public/bond-import'), path.join(assets, 'bond-import'), { recursive: true, filter: p => !p.endsWith('pdf.worker.min.mjs') });
for (const [name, target] of [['pdfjs-dist', 'pdfjs-LICENSE'], ['tesseract.js', 'tesseract-LICENSE'], ['tesseract.js-core', 'tesseract-core-LICENSE']]) {
  const license = path.join(web, 'node_modules', name, 'LICENSE');
  if (fs.existsSync(license)) fs.copyFileSync(license, path.join(assets, target));
}
fs.writeFileSync(path.join(assets, 'package.json'), '{"type":"module"}\n');
console.log('Copied client-side calculations and PDF/OCR engine with local assets.');
