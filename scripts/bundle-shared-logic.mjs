/** Build one DOM-free engine for Android's WebView and iOS JavaScriptCore. */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const root = path.resolve(import.meta.dirname, '..');
const web = process.argv[2] || '/Users/muhirwagabooreste/WebstormProjects/gabo';
const require = createRequire(path.join(web, 'package.json'));
const ts = require('typescript');
const names = ['calculations', 'pdf-import-parser', 'equity-pdf-parser', 'equity-validation', 'pdf-import-errors'];
const modules = names.map(name => {
  const source = fs.readFileSync(path.join(root, 'web-logic/source', name + '.ts'), 'utf8');
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
fs.writeFileSync(path.join(root,'app/shared/src/commonMain/composeResources/files/engine/logic.js'),bundle);
console.log('Bundled shared calculation and purchase parsing rules.');
