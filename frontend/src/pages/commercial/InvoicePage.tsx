import {useState} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {Alert,App,Button,Card,Form,Input,Modal,Select,Table,Tag} from 'antd';
import { localDateAfter } from './date';
import {useTranslation} from 'react-i18next';
import {hasPermission} from '../../access';
import {useAuth} from '../../auth/AuthProvider';
import {createInvoice,listDocuments,type CommercialDocument} from '../../api/commercial';
import {listSalesOrders} from '../../api/sales';
import {getErrorMessage} from '../../api/errors';
import {DocumentDetails,documentStatus} from './DocumentDetails';
import { CommercialSurface } from './CommercialSurface';

/** Same invoice and receipt controls are available from a sale and the commercial list. */
export function InvoicePage({salesOrderId,canIssue=true}:{salesOrderId?:number;canIssue?:boolean}):JSX.Element {
  const {t,i18n}=useTranslation();const en=i18n.language.startsWith('en');const L=(zh:string,english:string)=>en?english:zh;
  const {session}=useAuth();const {message}=App.useApp();const client=useQueryClient();const can=(p:string)=>hasPermission(session?.currentRole,session?.permissionCodes,p);
  const [page,setPage]=useState(0);const [selected,setSelected]=useState<CommercialDocument>();const [open,setOpen]=useState(false);const [busy,setBusy]=useState(false);const [key,setKey]=useState('');
  const [form]=Form.useForm<{salesOrderId:number;dueDate:string}>();
  const list=useQuery({queryKey:['commercial','invoices',salesOrderId,page],queryFn:()=>listDocuments('invoices',page,salesOrderId),enabled:can('invoice:view')});
  const orders=useQuery({queryKey:['sales-orders'],queryFn:()=>listSalesOrders(),enabled:open&&!salesOrderId});
  const changed=(d:CommercialDocument)=>{setSelected(d);void client.invalidateQueries({queryKey:['commercial','invoices']});};
  if(!can('invoice:view'))return <></>;
  return <CommercialSurface><Card size={salesOrderId?'small':'default'} style={{marginTop:16}} title={L('账单与收款','Invoices and receipts')} extra={canIssue&&can('invoice:create')?<Button type="primary" onClick={()=>{setKey(crypto.randomUUID());form.resetFields();form.setFieldsValue({salesOrderId,dueDate:localDateAfter(30)});setOpen(true);}}>{L('开立账单','Create invoice')}</Button>:null}>
    <p>{L('按已确认销售单开立账单。金额和收款独立于仓库发货进度；实际出库时扣减库存。','Invoice a confirmed sale. Billing and payment are independent of dispatch; stock is deducted on physical dispatch.')}</p>
    {list.error&&<Alert type="error" message={getErrorMessage(list.error,t)}/>}
    <Table rowKey="id" loading={list.isLoading} dataSource={list.data?.content??[]} pagination={{current:page+1,pageSize:20,total:list.data?.totalElements,onChange:p=>setPage(p-1),showSizeChanger:false}} columns={[
      {title:L('账单','Invoice'),dataIndex:'number',render:(v:string,d:CommercialDocument)=><Button type="link" onClick={()=>setSelected(d)}>{v}</Button>},
      ...(!salesOrderId?[{title:L('客户','Customer'),dataIndex:'customerName'},{title:L('销售单','Sales order'),dataIndex:'salesOrderNo'}]:[]),
      {title:L('账单 GBP','Total GBP'),dataIndex:'total',render:(n:number)=>n.toFixed(2)},{title:L('已收 GBP','Received GBP'),dataIndex:'received',render:(n:number)=>n.toFixed(2)},
      {title:L('待收 GBP','Outstanding GBP'),dataIndex:'outstanding',render:(n:number)=>n.toFixed(2)},{title:L('状态','Status'),render:(_,d)=><Tag>{documentStatus(d,en)}</Tag>},
    ]}/>
  </Card>
  <DocumentDetails document={selected} onClose={()=>setSelected(undefined)} onChanged={changed}/>
  <Modal open={open} title={L('开立商业账单','Create commercial invoice')} confirmLoading={busy} onCancel={()=>!busy&&setOpen(false)} onOk={()=>void form.validateFields().then(async v=>{setBusy(true);try{changed(await createInvoice(v.salesOrderId,v.dueDate,key));setOpen(false);}catch(e){message.error(getErrorMessage(e,t));}finally{setBusy(false);}})}>
    {orders.error&&<Alert type="error" message={getErrorMessage(orders.error,t)}/>}
    <Form form={form} layout="vertical"><Form.Item name="salesOrderId" label={L('已确认销售订单','Confirmed sales order')} rules={[{required:true}]}>
      {salesOrderId?<Input disabled/>:<Select showSearch optionFilterProp="label" loading={orders.isLoading} options={(orders.data??[]).filter(o=>['APPROVED','COMPLETED','CLOSED'].includes(o.commercialStatus??'')&&o.orderPurpose!=='EXCHANGE_REPLACEMENT').map(o=>({value:o.id,label:`${o.orderNo} · ${o.customerName}`}))}/>}
    </Form.Item><Form.Item name="dueDate" label={L('付款到期日','Payment due date')} rules={[{required:true}]}><Input type="date"/></Form.Item></Form>
  </Modal></CommercialSurface>;
}
