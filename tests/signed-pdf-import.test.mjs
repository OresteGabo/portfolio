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
function pdf(widget = '/FT /Sig', action = '', documentText = text, positioned = false) {
  const escape = line => line.replace(/[\\()]/g, '\\$&');
  const stream = positioned ? documentText.split('\n').map((line, i) => {
    const y = 1100 - i * 14;
    const purchase = line.match(/^(\S+ Purchase \d+#) (Purchase of .*?) (\d+ [\d,.]+ [\d,.]+ [\d,.]+)$/);
    if (!purchase) return `BT /F1 9 Tf 1 0 0 1 12 ${y} Tm (${escape(line)}) Tj ET`;
    // BK table descriptions sit 2.5pt above date/reference/amount cells.
    return [[12,y,purchase[1]],[240,y+2.5,purchase[2]],[700,y,purchase[3]]]
      .map(([x,baseline,value])=>`BT /F1 9 Tf 1 0 0 1 ${x} ${baseline} Tm (${escape(value)}) Tj ET`).join('\n');
  }).join('\n') : 'BT /F1 9 Tf 12 1100 Td ' + documentText.split('\n').map(line => `(${escape(line)}) Tj 0 -14 Td`).join('\n') + ' ET';
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

test('shipped reader keeps offset BK table cells and wrapped contract suffixes in the correct entry', async () => {
  const documentText = fs.readFileSync(new URL('fixtures/bk-statement-wrapped.txt', import.meta.url), 'utf8');
  const expected = globalThis.PortfolioLogic.parse(fs.readFileSync(new URL('fixtures/bk-statement.txt', import.meta.url), 'utf8'), false);
  const result = await read(pdf('/FT /Sig', '', documentText, true));
  assert.deepEqual(result, expected);
});

// Explicit opt-in local verification only: never commit a customer's original PDF.
if (process.env.GABO_TEST_STATEMENT_PDF) test('supplied original statement passes the shipped reader', async () => {
  const result = await read(new Uint8Array(fs.readFileSync(process.env.GABO_TEST_STATEMENT_PDF)));
  assert.equal(result.kind, 'statement');
  assert.ok(result.rows.length > 0);
  assert.equal(result.usedOcr, false);
});
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
