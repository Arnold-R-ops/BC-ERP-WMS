import type { Rma } from '../../api/rma';
export function refundProgress(rma: Pick<Rma,'refundAmount'|'refundCurrency'|'resolutionStatus'|'events'>): { expected?:number; confirmed:number; pending?:number } {
  const confirmations=new Map<string,number>();
  for(const event of rma.events) {
    if(event.eventType!=='RMA_REFUND_CONFIRMED')continue;
    try {const value=JSON.parse(event.payloadJson) as {reference?:string;amount?:number;currency?:string};
      if(value.reference && typeof value.amount==='number' && Number.isFinite(value.amount) && value.currency===rma.refundCurrency) confirmations.set(value.reference,value.amount);
    } catch { /* An unreadable event does not prove that money was returned. */ }
  }
  const confirmed=[...confirmations.values()].reduce((a,b)=>a+b,0);
  const expected=rma.refundAmount;
  return {expected,confirmed,pending:expected==null?undefined:Math.max(0,expected-confirmed)};
}
