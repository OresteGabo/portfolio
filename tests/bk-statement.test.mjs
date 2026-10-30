import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {parseBkStatement} from '../app/shared/src/commonMain/composeResources/files/engine/bk-statement-parser.js';
const text=fs.readFileSync(new URL('./fixtures/bk-statement.txt',import.meta.url),'utf8');
test('statement extracts purchases without treating deposits or fees as new investments',()=>{
 const result=parseBkStatement(text);
 assert.equal(result.kind,'statement'); assert.equal(result.rows.length,4);
 assert.deepEqual(result.rows.map(r=>r.kind),['bond','equity','bond','equity']);
 assert.equal(result.rows[1].contractNote,'10000000001#01');assert.equal(result.rows[3].contractNote,'10000000002#01');
 assert.equal(result.rows[1].amount,18400);
 assert.equal(result.rows[0].faceValue,undefined);assert.equal(result.rows[0].couponRate,undefined);
});
test('unrelated PDFs, missing boundaries, contradictory dates and negative debits cannot produce reminders',()=>{
 assert.equal(parseBkStatement('CLIENT STATEMENT from another provider'),null);
 for(const raw of [text.replace('Opening Balance As On','Opening'),text.replace('01-Jul-2026','01-Jul-2027'),text.replace('1,500,000.00','-1,500,000.00'),text.replace('Balance (RWF)','Balance (USD)'),text.replace('18,400.00','10,000.00')]) assert.throws(()=>parseBkStatement(raw));
});
test('shared JavaScriptCore engine accepts statement layout and marks OCR',()=>{
 const c=vm.createContext({});vm.runInContext(fs.readFileSync(new URL('../app/shared/src/commonMain/composeResources/files/engine/logic.js',import.meta.url),'utf8'),c);
 assert.deepEqual(JSON.parse(JSON.stringify(c.PortfolioLogic.parse(text,true))),parseBkStatement(text,true));
});
test('cash receipts and separately charged fees remain source evidence outside purchase rows',()=>{
 const r=parseBkStatement(text);
 assert.equal(r.rows.length,4);
 assert.deepEqual(r.movements.map(r=>r.kind),['cash-receipt','fee','fee']);
 assert.deepEqual(r.movements.map(r=>r.amount),[1511000,5000,5900]);
 assert.deepEqual(r.movements.map(r=>r.direction),['credit','debit','debit']);
 assert.ok([...r.rows,...r.movements].every(r=>r.rowNumber>0));
});
test('coupon and dividend movements never become holdings or verified income receipts',()=>{
 const r=parseBkStatement(text.replace('Closing Balance As On',
   '25-Sep-2026 Receipt COUPON 0.00 60,000.00\n26-Sep-2026 Receipt DIVIDEND 0.00 500.50\nClosing Balance As On'));
 assert.equal(r.rows.length,4);
 assert.deepEqual(r.movements.slice(-2).map(r=>r.kind),['coupon','dividend']);
 assert.ok(r.movements.slice(-2).every(r=>r.faceValue===undefined && r.shares===undefined && r.received===undefined));
});
