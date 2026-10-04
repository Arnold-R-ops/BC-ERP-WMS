import {describe,it,expect} from 'vitest';
import {refundProgress} from './refundProgress';
describe('return refund progress',()=>{
  it('does not count a request or failure as payment',()=>{
    expect(refundProgress({refundAmount:30,refundCurrency:'GBP',resolutionStatus:'REFUND_REQUESTED',events:[]})).toEqual({expected:30,confirmed:0,pending:30});
    expect(refundProgress({refundAmount:30,refundCurrency:'GBP',resolutionStatus:'EXCEPTION',events:[]})).toEqual({expected:30,confirmed:0,pending:30});
  });
  it('uses confirmed evidence once per reference',()=>{
    const event={id:1,eventType:'RMA_REFUND_CONFIRMED',payloadJson:JSON.stringify({reference:'BANK-1',amount:30,currency:'GBP'}),actorId:1,actorName:'Test',occurredAt:'2026-09-19'};
    expect(refundProgress({refundAmount:30,refundCurrency:'GBP',resolutionStatus:'REFUND_CONFIRMED',events:[event,event]})).toEqual({expected:30,confirmed:30,pending:0});
  });
});
