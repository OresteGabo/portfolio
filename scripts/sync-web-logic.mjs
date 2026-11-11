/** Copy only public, client-side investment logic. No private data or environment files. */
import fs from 'node:fs';
import {nativeModuleSource, nativePdfModuleOutput} from './native-pdf-module.mjs';
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
  const code = nativeModuleSource(original);
  const result = ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
  const destination = name === 'pdf-import' ? assets : common;
  const generated = name === 'pdf-import' ? nativePdfModuleOutput(result.outputText) : result.outputText;
  fs.writeFileSync(path.join(destination, `${name}.js`), generated);
}
fs.copyFileSync(path.join(web, 'node_modules/pdfjs-dist/legacy/build/pdf.mjs'), path.join(assets, 'pdf.mjs'));
fs.copyFileSync(path.join(web, 'node_modules/tesseract.js/dist/tesseract.esm.min.js'), path.join(assets, 'tesseract.mjs'));
// The importer uses OEM 1 (LSTM only). Keep accelerated and baseline embedded
// cores; raw .wasm files and legacy OCR engines are never loaded by this app.
const ocrCores = new Set(['tesseract-core-lstm.wasm.js', 'tesseract-core-simd-lstm.wasm.js', 'tesseract-core-relaxedsimd-lstm.wasm.js']);
const importAssets = path.join(assets, 'bond-import');
fs.rmSync(importAssets, {recursive: true, force: true});
fs.cpSync(path.join(web, 'public/bond-import'), importAssets, {
  recursive: true,
  filter: p => !p.endsWith('pdf.worker.min.mjs') &&
    (!path.basename(p).startsWith('tesseract-core') || ocrCores.has(path.basename(p))),
});
for (const [name, target] of [['pdfjs-dist', 'pdfjs-LICENSE'], ['tesseract.js', 'tesseract-LICENSE'], ['tesseract.js-core', 'tesseract-core-LICENSE']]) {
  const license = path.join(web, 'node_modules', name, 'LICENSE');
  if (fs.existsSync(license)) fs.copyFileSync(license, path.join(assets, target));
}
fs.writeFileSync(path.join(common, 'package.json'), '{"type":"module"}\n');
console.log('Copied client-side calculations and PDF/OCR engine with local assets.');

execFileSync(process.execPath, [path.join(root, 'scripts/bundle-shared-logic.mjs'), web], {stdio:'inherit'});
