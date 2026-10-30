import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const common = new URL('../app/shared/src/commonMain/composeResources/files/engine/', import.meta.url);
const android = new URL('../app/shared/src/androidMain/assets/engine/', import.meta.url);
vm.runInThisContext(fs.readFileSync(new URL('logic.js', common), 'utf8'));
// Run the shipped reader, changing only browser asset URLs to local Node URLs.
const source = fs.readFileSync(new URL('pdf-import.js', android), 'utf8')
  .replace(/from "\.\/([^\"]+)"/g, (_, name) => `from "${new URL(name, common).href}"`)
  .replace('import("./pdf.mjs")', `import("${new URL('pdf.mjs', android).href}")`)
  .replace('`/bond-import/pdf.worker.legacy.min.mjs?v=${encodeURIComponent(pdfjs.version)}`', JSON.stringify(new URL('bond-import/pdf.worker.legacy.min.mjs', android).href));
const { importBondPdf } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const text = fs.readFileSync(new URL('fixtures/bnr-market-results-2026.txt', import.meta.url), 'utf8');
function pdf(widget = '/FT /Sig', action = '', documentText = text) {
  const stream = 'BT /F1 9 Tf 12 1100 Td ' + documentText.split('\n').map(line => `(${line.replace(/[\\()]/g, '\\$&')}) Tj 0 -14 Td`).join('\n') + ' ET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [5 0 R 6 0 R] /SigFlags 3 >> >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 1000 1200] /Resources << /Font << /F1 7 0 R >> >> /Contents 4 0 R /Annots [5 0 R 6 0 R] >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`,
    `<< /Type /Annot /Subtype /Widget ${widget} /T (Signature1) /P 3 0 R /Rect [0 0 20 20] /F 132 ${action} >>`,
    '<< /Type /Annot /Subtype /Widget /FT /Sig /T (Signature2) /P 3 0 R /Rect [20 0 40 20] /F 132 >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let out = '%PDF-1.7\n'; const offsets = [0];
  objects.forEach((o, i) => { offsets.push(Buffer.byteLength(out)); out += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = Buffer.byteLength(out);
  out += `xref\n0 ${offsets.length}\n0000000000 65535 f \n` + offsets.slice(1).map(n => `${String(n).padStart(10, '0')} 00000 n \n`).join('');
  out += `trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return new Uint8Array(Buffer.from(out));
}
async function read(bytes) {
  return importBondPdf(new File([bytes], 'issuance.pdf', { type: 'application/pdf' }), new AbortController().signal, () => {}, bytes);
}
test('Android shipped PDF reader accepts passive signature fields and extracts shared issuance terms', async () => {
  const result = await read(pdf());
  assert.equal(result.kind, 'bond-issuance');
  assert.deepEqual(result.missingFields, []);
  assert.equal(result.fields.couponRate, .1315);
  assert.equal(result.fields.couponDates.length, 40);
  assert.equal(result.fields.faceValue, undefined);
});
test('signature widget actions remain blocked', async () => {
  await assert.rejects(read(pdf('/FT /Sig', '/AA << /U << /S /JavaScript /JS (void 0) >> >>')), e => e.code === 'unsafe_content');
});
test('ordinary form fields remain blocked', async () => {
  await assert.rejects(read(pdf('/FT /Tx')), e => e.code === 'unsafe_content');
});
if (process.env.GABO_TEST_ISSUANCE_PDF) test('supplied original signed BNR PDF passes the shipped Android reader', async () => {
  const result = await read(new Uint8Array(fs.readFileSync(process.env.GABO_TEST_ISSUANCE_PDF)));
  assert.equal(result.kind, 'bond-issuance');
  assert.deepEqual(result.missingFields, []);
  assert.equal(result.fields.couponDates.length, 40);
});

for (const [fixture, kind] of [['equity-purchase', 'equity'], ['bk-statement', 'statement'], ['bnr-investor-results-synthetic', 'bond']]) {
  test(`shipped PDF.js reader extracts the supported ${kind} fixture through the same dispatcher`, async () => {
    const documentText = fs.readFileSync(new URL(`fixtures/${fixture}.txt`, import.meta.url), 'utf8');
    const result = await read(pdf('/FT /Sig', '', documentText));
    assert.equal(result.kind, kind);
    assert.equal(result.usedOcr, false);
    if (kind === 'statement') assert.equal(result.rows.length, 4);
    else assert.deepEqual(result.missingFields, []);
  });
}
