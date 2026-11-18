/** Rebuild the retired JavaScript reference engine for migration tests only. Never packaged in the app. */
import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';
import { nativeModuleSource, nativePdfModuleOutput } from './native-pdf-module.mjs';
const root = path.resolve(import.meta.dirname, '..');
const args = process.argv.slice(2);
if (args.some(arg => arg !== '--check')) throw new Error('Usage: node scripts/bundle-shared-logic.mjs [--check]. Sources and tooling belong to this repository.');
const check = args.includes('--check');
const common = 'tests/reference-engine/common';
const android = 'tests/reference-engine/android';
const outputs = new Map();
// Generate every local source module, including the Android extraction adapter.
for (const file of fs.readdirSync(path.join(root, 'tests/reference-engine/source')).filter(name => name.endsWith('.ts')).sort()) {
  const name = file.slice(0, -3);
  const source = fs.readFileSync(path.join(root, 'tests/reference-engine/source', file), 'utf8');
  const esm = ts.transpileModule(nativeModuleSource(source), {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText;
  outputs.set(`${name === 'pdf-import' ? android : common}/${name}.js`, name === 'pdf-import' ? nativePdfModuleOutput(esm) : esm);
}
const names = ['calculations', 'pdf-import-parser', 'equity-pdf-parser', 'equity-validation', 'pdf-import-errors', 'bk-statement-parser', 'document-parser-support'];
const modules = names.map(name => {
  const source = fs.readFileSync(path.join(root, 'tests/reference-engine/source', name + '.ts'), 'utf8');
  const code = ts.transpileModule(source, {compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS}}).outputText;
  return `${JSON.stringify('./'+name)}: function(require,module,exports) {\n${code}\n}`;
}).join(',\n');
const bundle = `(function(global){
'use strict';
var modules={${modules}}, cache={};
function require(name){if(cache[name])return cache[name].exports;var module={exports:{}};cache[name]=module;modules[name](require,module,module.exports);return module.exports;}
var calc=require('./calculations'), pdf=require('./equity-pdf-parser');
global.PortfolioLogic={
project:function(input){var assumptions=Object.assign({},calc.DEFAULT_ASSUMPTIONS,input.assumptions);var lots=input.includeHoldings?input.purchases.map(function(p){return calc.purchaseToStartingLot(p,assumptions)}).filter(Boolean):undefined;var rows=calc.calculateProjection(assumptions,input.injections||[],lots);return {rows:rows,summary:calc.summarizeProjection(rows,assumptions)};},
parse:function(text,usedOcr){return pdf.parsePurchasePdfText(text,usedOcr);}
};
})(globalThis);\n`;
outputs.set(`${common}/logic.js`, bundle);
const stale = [];
for (const [relative, content] of outputs) {
  const destination = path.join(root, relative);
  if (check) {
    if (!fs.existsSync(destination) || fs.readFileSync(destination, 'utf8') !== content) stale.push(relative);
  } else {
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.writeFileSync(destination, content);
  }
}
if (stale.length) {
  console.error(`Generated engine files are stale. Run npm run generate:reference:\n${stale.join('\n')}`);
  process.exitCode = 1;
} else console.log(check ? 'Test-only reference engine matches its archived TypeScript sources.' : 'Generated the test-only reference engine; mobile runtime is Kotlin.');
