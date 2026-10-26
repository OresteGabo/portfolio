import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { parsePurchasePdfText } from '../app/shared/src/commonMain/composeResources/files/engine/equity-pdf-parser.js';
const report = fs.readFileSync(new URL('./fixtures/bnr-market-results-2026.txt', import.meta.url), 'utf8');

test('reads the supplied BNR report without turning auction totals into a purchase', () => {
  const r = parsePurchasePdfText(report);
  assert.equal(r.kind, 'bond-issuance');
  assert.deepEqual(r.missingFields, []);
  assert.deepEqual(r.warnings, []);
  assert.equal(r.fields.isin, 'RWBO20Y000686/MN');
  assert.equal(r.fields.bondName, 'FXD5/2026/20YR');
  assert.equal(r.fields.issuanceDate, '2026-10-21');
  assert.equal(r.fields.settlementDate, '2026-10-23');
  assert.equal(r.fields.maturityDate, '2046-09-28');
  assert.equal(r.fields.couponRate, .1315);
  assert.equal(r.fields.yieldRate, .1315);
  assert.equal(r.fields.amountOffered, 10_000_000_000);
  assert.equal(r.fields.appliedAmount, 45_106_600_000);
  assert.equal(r.fields.amountSold, 10_000_000_000);
  assert.equal(r.fields.bidCount, 101);
  assert.equal(r.fields.couponDates.length, 40);
  assert.equal(r.fields.couponDates.at(-1), '2046-09-28');
  assert.equal(r.fields.faceValue, undefined);
  assert.equal(r.fields.purchaseDate, undefined);
});

test('bundled engine used by both native platforms returns the same issuance', () => {
  const context = vm.createContext({});
  vm.runInContext(fs.readFileSync(new URL('../app/shared/src/commonMain/composeResources/files/engine/logic.js', import.meta.url), 'utf8'), context);
  assert.deepEqual(JSON.parse(JSON.stringify(context.PortfolioLogic.parse(report, false))), parsePurchasePdfText(report));
});

test('OCR retains review warning and incomplete auction figures prevent saving', () => {
  assert.ok(parsePurchasePdfText(report, true).warnings.length);
  assert.equal(parsePurchasePdfText(report, true).fields.scheduleConfidence, 'estimated');
  const missing = parsePurchasePdfText(report.replace('45,106,600,000', 'unreadable'));
  assert.ok(missing.missingFields.includes('appliedAmount'));
});

test('ambiguous mixed public and investor reports are not saved as issuances', () => {
  assert.throws(() => parsePurchasePdfText(report + '\nINVESTOR RESULTS\nSUMMARY MARKET RESULTS'));
});

test('Android PDF.js glyph spacing preserves rates, auction totals and every coupon date', () => {
  const extracted = fs.readFileSync(new URL('./fixtures/bnr-market-results-pdfjs-2026.txt', import.meta.url), 'utf8');
  assert.deepEqual(parsePurchasePdfText(extracted), parsePurchasePdfText(report));
});
