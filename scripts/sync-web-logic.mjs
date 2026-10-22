/** Copy only public, client-side investment logic. No private data or environment files. */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
const web = process.argv[2] || '/Users/muhirwagabooreste/WebstormProjects/gabo';
const require = createRequire(path.join(web, 'package.json'));
const ts = require('typescript');
const root = path.resolve(import.meta.dirname, '..');
const assets = path.join(root, 'app/shared/src/androidMain/assets/engine');
const common = path.join(root, 'app/shared/src/commonMain/composeResources/files/engine');
fs.mkdirSync(common, { recursive: true });
fs.mkdirSync(assets, { recursive: true });
const modules = ['calculations', 'pdf-import', 'pdf-import-parser', 'pdf-import-errors', 'pdf-import-diagnostics', 'pdf-file', 'equity-pdf-parser', 'equity-validation', 'types'];
for (const name of modules) {
  const original = fs.readFileSync(path.join(web, 'lib/bonds', `${name}.ts`), 'utf8');
  fs.writeFileSync(path.join(root, 'web-logic/source', `${name}.ts`), original);
  let code = original.replaceAll('"pdfjs-dist/legacy/build/pdf.mjs"', '"./pdf.mjs"').replaceAll('"tesseract.js"', '"./tesseract.mjs"');
  code = code.replace(/(from\s+|import\()("|')(\.\/[^"']+)\2/g, (_, lead, quote, target) => `${lead}${quote}${/\.(mjs|js)$/.test(target) ? target : target + '.js'}${quote}`);
  const result = ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
  const destination = name === 'pdf-import' ? assets : common;
  const generated = name === 'pdf-import' ? result.outputText.replace('import { parsePurchasePdfText } from \"./equity-pdf-parser.js\";', 'const parsePurchasePdfText = (text, usedOcr) => PortfolioLogic.parse(text, usedOcr);') : result.outputText;
  fs.writeFileSync(path.join(destination, `${name}.js`), generated);
}
fs.copyFileSync(path.join(web, 'node_modules/pdfjs-dist/legacy/build/pdf.mjs'), path.join(assets, 'pdf.mjs'));
fs.copyFileSync(path.join(web, 'node_modules/tesseract.js/dist/tesseract.esm.min.js'), path.join(assets, 'tesseract.mjs'));
fs.cpSync(path.join(web, 'public/bond-import'), path.join(assets, 'bond-import'), { recursive: true, filter: p => !p.endsWith('pdf.worker.min.mjs') });
for (const [name, target] of [['pdfjs-dist', 'pdfjs-LICENSE'], ['tesseract.js', 'tesseract-LICENSE'], ['tesseract.js-core', 'tesseract-core-LICENSE']]) {
  const license = path.join(web, 'node_modules', name, 'LICENSE');
  if (fs.existsSync(license)) fs.copyFileSync(license, path.join(assets, target));
}
fs.writeFileSync(path.join(common, 'package.json'), '{"type":"module"}\n');
console.log('Copied client-side calculations and PDF/OCR engine with local assets.');

execFileSync(process.execPath, [path.join(root, 'scripts/bundle-shared-logic.mjs'), web], {stdio:'inherit'});
