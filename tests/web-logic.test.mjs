import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { DEFAULT_ASSUMPTIONS, calculateProjection, summarizeProjection } from '../app/androidApp/src/main/assets/engine/calculations.js';
import { parsePurchasePdfText } from '../app/androidApp/src/main/assets/engine/equity-pdf-parser.js';
import { validateBondPdfEnvelope } from '../app/androidApp/src/main/assets/engine/pdf-import-errors.js';

test('one year without allocation preserves all contributions in cash', () => {
  const assumptions = {...DEFAULT_ASSUMPTIONS, horizonYears:1, auctionFillRate:0, contributionPeriods:[]};
  const rows = calculateProjection(assumptions);
  assert.equal(rows.length,12);
  assert.equal(rows.at(-1).totalAccountValue,3600000);
  assert.equal(rows.at(-1).closingPortfolio,0);
  assert.equal(summarizeProjection(rows, assumptions).totalCoupons,0);
});
test('cash injections enter the configured month exactly once', () => {
  const assumptions = {...DEFAULT_ASSUMPTIONS, horizonYears:1, monthlyContribution:0, auctionFillRate:0, contributionPeriods:[]};
  const rows = calculateProjection(assumptions,[{id:'one',label:'One',amount:1000000,month:3}]);
  assert.equal(rows[1].totalAccountValue,0);
  assert.equal(rows[2].totalAccountValue,1000000);
  assert.equal(rows.at(-1).totalAccountValue,1000000);
});
test('overlapping contribution periods add and stop on their last month', () => {
  const rows = calculateProjection({...DEFAULT_ASSUMPTIONS,horizonYears:1,auctionFillRate:0,contributionPeriods:[{id:'a',amount:100,startMonth:1,endMonth:3},{id:'b',amount:200,startMonth:2,endMonth:2}]});
  assert.deepEqual(rows.slice(0,4).map(row=>row.personalContribution),[100,300,100,0]);
});
test('equity PDF text detection preserves fees, dates and purchase totals', () => {
  const text = fs.readFileSync(new URL('./fixtures/equity-purchase.txt',import.meta.url),'utf8');
  const result = parsePurchasePdfText(text);
  assert.equal(result.kind,'equity');
  assert.equal(result.fields.shares,500);
  assert.equal(result.fields.netAmountPayable,340728.5);
  assert.equal(result.fields.tradeDate,'2026-09-24');
  assert.deepEqual(result.missingFields,[]);
  assert.throws(()=>parsePurchasePdfText(text.replace('EQUITY PURCHASE','EQUITY SALE')));
});
test('PDF envelope rejects trailing executable content', () => {
  assert.doesNotThrow(()=>validateBondPdfEnvelope(Buffer.from('%PDF-1.4\nbody\n%%EOF\n')));
  assert.throws(()=>validateBondPdfEnvelope(Buffer.from('%PDF-1.4\nbody\n%%EOF\n<script>bad()</script>')));
});
