import { Alert, App, Button, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { closeDocument, exportDocument, recordReceipt, reverseReceipt, type CommercialDocument, type ReceiptInput } from '../../api/commercial';
import { saveBlob } from '../../api/files';
import { getErrorMessage } from '../../api/errors';
import { localDateAfter } from './date';

export function documentStatus(d: CommercialDocument, english=false): string {
  if (d.kind==='INVOICE' && d.status==='ISSUED') return d.outstanding===0 ? (english?'Paid':'已收清') : d.received>0 ? (english?'Part paid':'部分收款') : (english?'Unpaid':'待收款');
  return ({OPEN:english?'Open':'待确认报价',CONVERTED:english?'Converted':'已转订单',CANCELLED:english?'Cancelled':'已取消',VOID:english?'Void':'已作废'} as Record<string,string>)[d.status]??d.status;
}
export function DocumentDetails({document:d,onClose,onChanged,onEdit,onConvert}: {document?:CommercialDocument;onClose:()=>void;onChanged:(d:CommercialDocument)=>void;onEdit?:(d:CommercialDocument)=>void;onConvert?:(d:CommercialDocument)=>void}): JSX.Element {
  const {session}=useAuth();const {message}=App.useApp();const {t,i18n}=useTranslation();const en=i18n.language.startsWith('en');const L=(zh:string,english:string)=>en?english:zh;
  const can=(p:string)=>hasPermission(session?.currentRole,session?.permissionCodes,p);
  const [busy,setBusy]=useState(false);const [receiptOpen,setReceiptOpen]=useState(false);const [requestKey,setRequestKey]=useState('');
  const [reasonAction,setReasonAction]=useState<number|'close'>();const [reason,setReason]=useState('');
  const [form]=Form.useForm<ReceiptInput>();
  const kind=d?.kind==='QUOTE'?'quotes':'invoices';
  async function run(action:()=>Promise<CommercialDocument>):Promise<boolean>{setBusy(true);try{onChanged(await action());return true;}catch(e){message.error(getErrorMessage(e,t));return false;}finally{setBusy(false);}}
  async function download(format:'pdf'|'xlsx'){if(!d)return;setBusy(true);try{saveBlob(await exportDocument(kind,d.id,format),`${d.number}.${format}`);}catch(e){message.error(getErrorMessage(e,t));}finally{setBusy(false);}}
  return <><Drawer open={!!d} width={980} title={d?.number} onClose={onClose} destroyOnHidden>{d && <>
    <Space wrap style={{marginBottom:16}}><Tag>{documentStatus(d,en)}</Tag>
      {can('commercial:export') && <Button loading={busy} onClick={()=>void download('pdf')}>PDF</Button>}
      {d.kind==='QUOTE' && can('commercial:export') && <Button loading={busy} onClick={()=>void download('xlsx')}>{L('客户反馈 Excel','Feedback Excel')}</Button>}
      {d.kind==='QUOTE' && d.status==='OPEN' && can('quotation:edit') && <Button onClick={()=>onEdit?.(d)}>{L('编辑报价','Edit quote')}</Button>}
      {d.kind==='QUOTE' && d.status==='OPEN' && can('quotation:convert') && can('sales:create') && <Button type="primary" loading={busy} onClick={()=>onConvert?.(d)}>{L('转销售订单','Convert to order')}</Button>}
      {d.kind==='INVOICE' && d.status==='ISSUED' && d.outstanding>0 && can('invoice:receive') && <Button type="primary" onClick={()=>{setRequestKey(crypto.randomUUID());form.resetFields();form.setFieldsValue({amount:d.outstanding,paymentDate:localDateAfter(),method:'BANK_TRANSFER'});setReceiptOpen(true);}}>{L('登记收款','Record receipt')}</Button>}
      {((d.kind==='QUOTE' && d.status==='OPEN' && can('quotation:edit')) || (d.kind==='INVOICE' && d.status==='ISSUED' && d.received===0 && can('invoice:create'))) && <Button danger onClick={()=>{setReason('');setReasonAction('close');}}>{L(d.kind==='QUOTE'?'取消报价':'作废账单',d.kind==='QUOTE'?'Cancel quote':'Void invoice')}</Button>}
    </Space>
    {d.orderAmountChanged && <Alert type="warning" showIcon style={{marginBottom:16}} message={L('原订单数量已变更，请核对账单。未收款账单可作废后重新开立；已收款的退货沿用原单退货流程。','Order quantities changed. Review this invoice; unpaid invoices can be voided and reissued. Use the linked return process for paid returns.')} />}
    <Descriptions column={2} items={[
      {key:'customer',label:L('客户','Customer'),children:d.customerName}, {key:'date',label:L('日期','Date'),children:d.documentDate},
      {key:'due',label:L(d.kind==='QUOTE'?'有效期':'到期日',d.kind==='QUOTE'?'Valid until':'Due date'),children:d.dueDate},
      {key:'order',label:L('关联销售单','Sales order'),children:d.salesOrderNo??'—'},
      {key:'bill',label:L('账单地址','Billing address'),children:d.billingAddress??'—'}, {key:'ship',label:L('收货地址','Shipping address'),children:d.shippingAddress??'—'},
      {key:'total',label:L('合计 GBP','Total GBP'),children:d.total.toFixed(2)},
      ...(d.kind==='INVOICE'?[{key:'received',label:L('已收 GBP','Received GBP'),children:d.received.toFixed(2)},{key:'dueAmount',label:L('待收 GBP','Outstanding GBP'),children:d.outstanding.toFixed(2)}]:[]),
    ]}/>
    <Table rowKey="rowKey" dataSource={d.lines.map((line,index)=>({...line,rowKey:index}))} pagination={false} scroll={{x:750}} columns={[
      {title:'SKU',dataIndex:'skuCode'},{title:L('商品','Product'),dataIndex:'description'}, {title:L('数量','Quantity'),dataIndex:'quantity'}, {title:L('单位','Unit'),dataIndex:'unit'},
      {title:L('单价 GBP','Unit price GBP'),dataIndex:'unitPrice',render:(n:number)=>n.toFixed(2)}, {title:L('金额 GBP','Amount GBP'),dataIndex:'amount',render:(n:number)=>n.toFixed(2)},
    ]}/>
    <p>{L('商品单价合计，未另行计算税费或运费。','Item prices; no additional tax or freight calculated.')}</p>
    {d.terms && <p style={{whiteSpace:'pre-wrap'}}>{d.terms}</p>}{d.closeReason && <Alert message={d.closeReason} />}
    {d.kind==='INVOICE' && <><h3>{L('收款记录','Receipts')}</h3><Table rowKey="id" dataSource={d.receipts} pagination={false} columns={[
      {title:L('日期','Date'),dataIndex:'paymentDate'},{title:L('类型','Type'),dataIndex:'kind',render:(k:string)=>k==='RECEIPT'?L('收款','Receipt'):L('录入冲销','Entry reversal')},
      {title:'GBP',dataIndex:'amount',render:(n:number)=>n.toFixed(2)},{title:L('方式','Method'),dataIndex:'method',render:(v:string)=>({BANK_TRANSFER:L('银行转账','Bank transfer'),CASH:L('现金','Cash'),CARD:L('银行卡','Card'),OTHER:L('其他','Other')}[v]??v)},
      {title:L('凭据/备注','Reference / note'),render:(_,r)=>[r.reference,r.note].filter(Boolean).join(' · ')},
      {title:L('操作','Action'),render:(_,r)=>d.status==='ISSUED' && r.kind==='RECEIPT' && !d.receipts.some(x=>x.reversesId===r.id) && can('invoice:receive') ? <Button size="small" onClick={()=>{setReason('');setReasonAction(r.id);}}>{L('更正录入','Correct entry')}</Button>:null},
    ]}/></>}
  </>}</Drawer>
  <Modal open={receiptOpen} title={L('登记已收到的款项','Record money received')} confirmLoading={busy} onCancel={()=>!busy&&setReceiptOpen(false)} onOk={()=>void form.validateFields().then(async v=>{if(d && await run(()=>recordReceipt(d.id,{...v,requestKey})))setReceiptOpen(false);})}>
    <Form form={form} layout="vertical"><Form.Item name="amount" label="GBP" rules={[{required:true}]}><InputNumber min={0.01} max={d?.outstanding} precision={2}/></Form.Item>
      <Form.Item name="paymentDate" label={L('收款日期','Payment date')} rules={[{required:true}]}><Input type="date" max={localDateAfter()}/></Form.Item>
      <Form.Item name="method" label={L('收款方式','Payment method')} rules={[{required:true}]}><Select options={[['BANK_TRANSFER',L('银行转账','Bank transfer')],['CASH',L('现金','Cash')],['CARD',L('银行卡','Card')],['OTHER',L('其他','Other')]].map(([value,label])=>({value,label}))}/></Form.Item>
      <Form.Item name="reference" label={L('银行流水号或收据号（可选）','Bank or receipt reference (optional)')}><Input maxLength={120}/></Form.Item>
      <Form.Item name="note" label={L('备注','Note')}><Input.TextArea maxLength={500}/></Form.Item>
    </Form>
  </Modal>
  <Modal open={reasonAction!==undefined} title={reasonAction==='close'?L('关闭单据','Close document'):L('更正收款录入','Correct receipt entry')} confirmLoading={busy} onCancel={()=>!busy&&setReasonAction(undefined)} okButtonProps={{disabled:!reason.trim()}} onOk={()=>void (async()=>{if(!d||reasonAction===undefined)return;const action=reasonAction; if(await run(()=>action==='close'?closeDocument(kind,d.id,reason):reverseReceipt(d.id,action,reason)))setReasonAction(undefined);})()}>
    {reasonAction!=='close' && <p>{L('用于录错款项的冲销，不会向客户退款。正式退款请使用关联原单的退货流程。','Corrects an entry error; does not refund the customer. Use the linked return process for refunds.')}</p>}
    <Input.TextArea aria-label={L('原因','Reason')} placeholder={L('填写原因','Enter a reason')} maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/>
  </Modal></>;
}
