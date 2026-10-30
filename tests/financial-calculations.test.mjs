import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {purchaseToStartingLot,calculateProjection,DEFAULT_ASSUMPTIONS} from '../app/shared/src/commonMain/composeResources/files/engine/calculations.js';
import {parseEquityPurchase} from '../app/shared/src/commonMain/composeResources/files/engine/equity-validation.js';
const reference=JSON.parse(fs.readFileSync(new URL('fixtures/investment-reference.json',import.meta.url),'utf8'));
const assumptions={...DEFAULT_ASSUMPTIONS,startYear:2027,startMonth:1,horizonYears:1,contributionPeriods:[],monthlyContribution:0,auctionFillRate:0,reinvestmentRate:1};
const record=b=>({id:b.id,status:'active',faceValue:b.principal,couponRate:b.couponRate,withholdingTaxRate:b.taxRate,couponFrequency:b.frequency,
  purchaseDate:'2026-07-01',settlementDate:'2026-07-01',maturityDate:b.maturityMonth+'-31',tenorYears:7,
  amountInvested:b.principal+11000,couponDates:['2027-01-15'],firstCouponDate:'2027-01-15'});
for(const b of reference.bonds) test(`existing simulator preserves reference gross/net coupon for ${b.id}`,()=>{
 const lot=purchaseToStartingLot(record(b),assumptions);
 assert.equal(lot.grossCouponAmount,b.grossCoupon);assert.equal(lot.netCouponAmount,b.netCoupon);
 const rows=calculateProjection(assumptions,[],[lot]);
 assert.equal(rows[0].couponPayment,b.netCoupon);assert.equal(rows[0].couponPayments[0].grossCouponAmount,b.grossCoupon);
 assert.equal(rows.at(-1).totalCoupons,b.netCoupon);
 assert.equal(rows.at(-1).totalAccountValue,b.principal+b.netCoupon); // Principal and coupon credited once each.
});
for(const p of reference.equities) test(`existing equity validation preserves ${p.ticker} acquisition cost including fees once`,()=>{
 const row={instrumentType:'equity',exchange:'RSE',currency:'RWF',securityName:'Synthetic reference',isin:'RWTESTREF0001',ticker:p.ticker,
  tradeDate:'2026-09-24',settlementDate:'2026-09-28',shares:p.shares,pricePerShare:p.price,grossConsideration:p.gross,
  brokerageCommission:p.fees[0],rseTransactionFee:p.fees[1],csdTransactionLevy:p.fees[2],cmaTransactionFee:p.fees[3],totalCharges:p.totalFees,netAmountPayable:p.payable};
 assert.equal(parseEquityPurchase(row).netAmountPayable,p.payable);
 assert.equal(parseEquityPurchase({...row,netAmountPayable:p.payable+p.totalFees}),null);
});
test('actual distinct dates in the same month are counted individually while duplicates are not',()=>{
 const lot=purchaseToStartingLot({...record(reference.bonds[0]),couponDates:['2027-01-15','2027-01-31','2027-01-15']},assumptions);
 const rows=calculateProjection(assumptions,[],[lot]);
 assert.equal(rows[0].couponPayments.length,2);assert.equal(rows[0].couponPayment,240350);
 assert.equal(rows.at(-1).totalCoupons,240350);
});
test('zero coupon holdings retain principal until maturity without inventing coupon income',()=>{
 const lot=purchaseToStartingLot({...record(reference.bonds[1]),couponRate:0,couponDates:[],maturityDate:'2027-08-31'},assumptions);
 assert.ok(lot);
 const rows=calculateProjection(assumptions,[],[lot]);
 assert.equal(rows[7].maturedPrincipal,1000000);assert.equal(rows[7].closingPortfolio,0);
 assert.equal(rows.at(-1).totalCoupons,0);assert.equal(rows.at(-1).totalAccountValue,1000000);
 assert.equal(rows.slice(8).reduce((sum,row)=>sum+row.maturedPrincipal,0),0);
});
