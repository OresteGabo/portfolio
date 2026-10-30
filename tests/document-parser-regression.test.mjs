import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {parsePurchasePdfText, parseEquityPdfText} from '../app/shared/src/commonMain/composeResources/files/engine/equity-pdf-parser.js';
import {parseBondPdfText} from '../app/shared/src/commonMain/composeResources/files/engine/pdf-import-parser.js';
import {parseBkStatement} from '../app/shared/src/commonMain/composeResources/files/engine/bk-statement-parser.js';
const fixture = name => fs.readFileSync(new URL(`fixtures/${name}.txt`, import.meta.url), 'utf8');
const equity = fixture('equity-purchase'), statement = fixture('bk-statement');
const bond = fixture('bnr-investor-results-synthetic'), issuance = fixture('bnr-market-results-2026');
const context = vm.createContext({});
vm.runInContext(fs.readFileSync(new URL('../app/shared/src/commonMain/composeResources/files/engine/logic.js', import.meta.url), 'utf8'), context);
const blocked = text => {
  try {
    const result = parsePurchasePdfText(text);
    assert.ok(result.missingFields?.length || result.warnings?.some(w => /do not agree|unambiguously|differs|invalid/i.test(w)), 'invalid extraction must not be save-ready');
  } catch (error) {
    // Assertion failures must never be mistaken for a parser rejection.
    if (error.code !== 'not_bond_result') throw error;
  }
};

test('every supported normalized result identifies institution, document type, format and parser version', () => {
  for (const [text, institution, documentType, format] of [
    [bond, 'BNR', 'bond-confirmation', 'investor-results'],
    [issuance, 'BNR', 'bond-issuance', 'summary-market-results'],
    [equity, 'BK Capital', 'equity-purchase', 'rse-purchase-contract-note'],
    [statement, 'BK Capital', 'statement', 'rwf-client-statement'],
  ]) {
    const result = parsePurchasePdfText(text);
    assert.deepEqual(result.parser, {institution, documentType, format, version: 1});
    for (const ocr of [false, true]) {
      assert.deepEqual(JSON.parse(JSON.stringify(context.PortfolioLogic.parse(text, ocr))), parsePurchasePdfText(text, ocr));
    }
  }
});

test('BK purchase preserves normalized money, optional empty fields and long identifiers without number coercion', () => {
  const result = parsePurchasePdfText(equity);
  assert.deepEqual(result.fields, {
    instrumentType:'equity', exchange:'RSE', securityName:'BANK OF KIGALI', ticker:'BOK', isin:'RW000A1JCYA',
    tradeDate:'2026-09-24', settlementDate:'2026-09-28', shares:500, pricePerShare:670,
    grossConsideration:335000, brokerageCommission:5025, rseTransactionFee:469, csdTransactionLevy:167.5,
    cmaTransactionFee:67, totalCharges:5728.5, netAmountPayable:340728.5, broker:'BK Capital',
    contractNote:'TEST-2026#01', cdsAccountNo:'', cdsReference:'', tradeReportId:'1234567890123456789',
    currency:'RWF', status:'active', notes:'',
  });
  assert.deepEqual(result.missingFields, []);
  assert.deepEqual(result.warnings, []);
  assert.ok(parsePurchasePdfText(equity, true).warnings.length);
});

for (const token of ['-5,025.00', '5,025.001', '5,02,5.00', '5025e0', '(5,025.00)', '5,025.00%']) {
  test(`BK fees reject the entire malformed token ${token}`, () => blocked(equity.replace('5,025.00', token)));
}
for (const token of ['500.5', '500oops', '5,00', '-500', '5e2']) {
  test(`BK quantities cannot be silently shortened: ${token}`, () => blocked(equity.replace('Shares : 500', `Shares : ${token}`)));
}
for (const date of ['31-Sep-2026', '124-Sep-2026', '24-Septemberish-2026', '24-Sep-20260']) {
  test(`BK rejects malformed trade date ${date}`, () => blocked(equity.replace('24-Sep-2026', date)));
}

test('BK settlement precedes trade, totals mismatch, missing fees and unknown securities block confirmation', () => {
  for (const text of [equity.replace('28-Sep-2026', '23-Sep-2026'), equity.replace('340,728.50', '340,729.50'),
    equity.replace('CSD Transaction Levy 0.0500% 167.50\n', ''), equity.replace('RW000A1JCYA', 'RWTESTUNKNOWN')]) blocked(text);
});
test('an unsupported institution cannot inherit BK defaults from a similar purchase-note layout', () => {
  assert.equal(parseEquityPdfText(equity.replaceAll('BK CAPITAL', 'OTHER BROKER')), null);
  assert.throws(() => parsePurchasePdfText(equity.replaceAll('BK CAPITAL', 'OTHER BROKER')), e => e.code === 'not_bond_result');
});
test('conflicting repeated labels and multiple contracts do not choose the first matching value', () => {
  for (const text of [equity + '\nTotal Charges 9,999.00', equity + '\nPrice : 900.00', equity + '\n' + equity,
    bond + '\nPRICE 100', bond + '\nBOND CODE RWTEST000002/MN', statement + '\nFrom Date : 01-Aug-2026']) blocked(text);
});
test('all mixed document families are rejected regardless of dispatch order or direct parser entry point', () => {
  for (const [a,b] of [[equity,statement],[equity,bond],[equity,issuance],[statement,bond],[statement,issuance],[bond,issuance]]) {
    for (const text of [a+'\n'+b,b+'\n'+a]) {
      for (const parse of [parsePurchasePdfText,parseEquityPdfText,parseBkStatement,parseBondPdfText]) {
        assert.throws(() => parse(text), e => e.code === 'not_bond_result');
      }
      assert.throws(() => context.PortfolioLogic.parse(text,false), e => e.code === 'not_bond_result');
    }
  }
});
for (const token of ['-1,000,000', '1,00,000', '1,000,000.001', '1000000e0']) {
  test(`BNR investor allocation rejects malformed token ${token}`, () => {
    const parsed = parseBondPdfText(bond.replace(' 1,000,000 ', ` ${token} `));
    assert.equal(parsed.fields.faceValue, undefined);
    assert.ok(parsed.missingFields.includes('Your allocated principal'));
  });
}
test('BNR does not borrow a missing date from the next label or accept a price prefix', () => {
  assert.equal(parseBondPdfText(bond.replace('14/08/2026', '')).fields.settlementDate, undefined);
  for (const token of ['98.5garbage','98.5.0','-98.5','9,8.5']) {
    assert.equal(parseBondPdfText(bond.replace('PRICE 98.5', `PRICE ${token}`)).fields.pricePercent, undefined);
  }
});
test('BNR accepts ungrouped allocations and preserves principal separately from auction totals', () => {
  const parsed = parsePurchasePdfText(bond.replace('2,000,000 1,000,000 985,000 60,000','2000000 1000000 985000 60000'));
  assert.equal(parsed.fields.faceValue,1000000);
  assert.equal(parsed.fields.couponDates.length,40);
  assert.deepEqual(parsed.missingFields,[]);
});
test('statement boundaries, malformed row dates/amounts and concatenated statements fail closed', () => {
  for (const text of [statement.replace('Closing Balance As On','Closing'), statement+'\n'+statement,
    statement.replace('22-Jul-2026 Purchase','22-Jul-20260 Purchase'),
    statement.replace('18,400.00','18,400.001'), statement.replace('18,400.00','18,40,0.00'),
    statement.replace('1,500,000.00','-1,500,000.00')]) {
    assert.throws(() => parsePurchasePdfText(text), e => e.code === 'not_bond_result');
  }
});
test('public auction totals reject malformed grouping and excess decimal precision', () => {
  for (const amount of ['45,10,600,000', '-45,106,600,000', '45,106,600,000.001']) {
    const result = parsePurchasePdfText(issuance.replace('45,106,600,000', amount));
    assert.ok(result.missingFields.includes('appliedAmount'));
  }
});
test('BK security identifiers cannot match a known ISIN prefix', () => {
  for (const isin of ['RW000A1JCYA-extra', 'RW000A1JCYA123', 'RW000A1JCYA?']) blocked(equity.replace('RW000A1JCYA', isin));
});
test('separated negative signs are not discarded from financial amounts', () => {
  blocked(equity.replace('5,025.00','- 5,025.00'));
  blocked(bond.replace(' 1,000,000 ',' - 1,000,000 '));
  assert.throws(() => parsePurchasePdfText(statement.replace('18,400.00','- 18,400.00')), e => e.code === 'not_bond_result');
  assert.ok(parsePurchasePdfText(issuance.replace('45,106,600,000','- 45,106,600,000')).missingFields.includes('appliedAmount'));
});
test('auction yield must be a bounded decimal, not an exponent or non-number', () => {
  for (const token of ['1.315e1','NaN','-13.15','101']) {
    const result = parsePurchasePdfText(issuance.replace(/Y\s*ield rate 13.15\s*%/i, `Yield rate ${token}%`));
    assert.ok(result.missingFields.includes('yieldRate'));
  }
});
test('BNR retains compact OCR tenor labels but not fractional or malformed years', () => {
  assert.equal(parseBondPdfText(bond.replace('20 Years', '20Years')).fields.tenorYears, 20);
  for (const token of ['20.5 Years','20oops Years']) {
    assert.equal(parseBondPdfText(bond.replace('20 Years', token)).fields.tenorYears, undefined);
  }
});
test('concatenated BNR reports cannot select the first allocation or auction', () => {
  for (const text of [bond+'\n'+bond.replace('1,000,000','900,000'), issuance+'\n'+issuance]) {
    assert.throws(() => parsePurchasePdfText(text), e => e.code === 'not_bond_result');
  }
});
