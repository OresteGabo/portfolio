import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {nativeModuleSource,nativePdfModuleOutput} from '../scripts/native-pdf-module.mjs';

test('native import adaptation uses the bundled default-export OCR facade',()=>{
  const source='const {createWorker, PSM} = await import("tesseract.js");\nimport {x} from "./pdf-file";';
  const adapted=nativeModuleSource(source);
  assert.match(adapted,/import\("\.\/native-tesseract\.mjs"\)/);
  assert.match(adapted,/"\.\/pdf-file\.js"/);
  const generated=fs.readFileSync(new URL('../app/shared/src/androidMain/assets/engine/pdf-import.js',import.meta.url),'utf8');
  assert.match(generated,/import\("\.\/native-tesseract\.mjs"\)/);
  assert.doesNotMatch(generated,/__guestDebug|__guestWorkerError/);
});
test('vendored browser OCR API is available through native named exports without starting a worker',async()=>{
  const previous=globalThis.self;
  globalThis.self=globalThis;
  try {
    const module=await import('../app/shared/src/androidMain/assets/engine/native-tesseract.mjs');
    assert.equal(typeof module.createWorker,'function');
    assert.equal(module.PSM.SPARSE_TEXT,"11");
  } finally { if(previous===undefined)delete globalThis.self;else globalThis.self=previous; }
});
test('native extraction still uses the existing shared financial parser',()=>{
  assert.equal(nativePdfModuleOutput('import { parsePurchasePdfText } from "./equity-pdf-parser.js";'),
    'const parsePurchasePdfText = (text, usedOcr) => PortfolioLogic.parse(text, usedOcr);');
});

test('offline OCR retains accelerated LSTM engines and no redundant standalone WebAssembly', () => {
  const base = new URL('../app/shared/src/androidMain/assets/engine/bond-import/', import.meta.url);
  assert.deepEqual(fs.readdirSync(new URL('tesseract/', base)).sort(), [
    'tesseract-core-lstm.wasm.js',
    'tesseract-core-relaxedsimd-lstm.wasm.js',
    'tesseract-core-simd-lstm.wasm.js',
  ]);
  // OEM 1 guarantees that the worker never requests the removed legacy cores.
  const importer = fs.readFileSync(new URL('../app/shared/src/androidMain/assets/engine/pdf-import.js', import.meta.url), 'utf8');
  assert.match(importer, /createWorker\("eng", 1,/);
  assert.ok(fs.statSync(new URL('eng.traineddata.gz', base)).size > 0);
  // These builds embed their own Wasm module (base64 starts with the Wasm magic).
  for (const name of fs.readdirSync(new URL('tesseract/', base))) {
    assert.match(fs.readFileSync(new URL(`tesseract/${name}`, base), 'utf8'), /AGFzbQ/);
  }
});
