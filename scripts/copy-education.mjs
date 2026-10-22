import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
const web = process.argv[2] || '/Users/muhirwagabooreste/WebstormProjects/gabo';
const require = createRequire(path.join(web, 'package.json'));
const ts = require('typescript');
const React = require('react');
const server = require('react-dom/server');
function load(name) {
  const original = fs.readFileSync(path.join(web, 'component/bonds', name + '.tsx'), 'utf8');
  const code = ts.transpileModule(original, {compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
  const module = {exports:{}};
  const customRequire = id => {
    if(id === 'lucide-react') return new Proxy({}, {get:()=>()=>null});
    if(id === 'next/link') return {__esModule:true, default: props => React.createElement('a', {href:props.href}, props.children)};
    if(id.includes('ImigongoBackground')) return {ImigongoBackground:()=>null};
    if(id.includes('BondSiteChrome')) return {GaboBrand:()=>null,BondThemeToggle:()=>null};
    if(id === './CopyOrderTemplate') return load('CopyOrderTemplate');
    return require(id);
  };
  new Function('require','module','exports',code)(customRequire,module,module.exports);
  return module.exports;
}
const {BondEducation} = load('BondEducation');
fs.writeFileSync('/private/tmp/portfolio-education.html', server.renderToStaticMarkup(React.createElement(BondEducation)));
console.log('Rendered the original education content for native text extraction.');

execFileSync("python3", [path.join(import.meta.dirname, "extract-education.py")], {stdio:"inherit"});
