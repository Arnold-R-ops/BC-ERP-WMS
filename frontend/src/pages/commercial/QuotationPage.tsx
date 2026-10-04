import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag } from 'antd';
import { localDateAfter } from './date';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { convertQuote, getCompanyProfile, listDocuments, saveCompanyProfile, saveQuote, type CommercialDocument, type CompanyProfile, type QuoteInput } from '../../api/commercial';
import { listCustomers } from '../../api/masterData';
import { listProductSkus } from '../../api/productSkus';
import { getErrorMessage } from '../../api/errors';
import { DocumentDetails, documentStatus } from './DocumentDetails';
import { CommercialSurface } from './CommercialSurface';

export function QuotationPage():JSX.Element {
  const {t,i18n}=useTranslation();const en=i18n.language.startsWith('en');const L=(zh:string,english:string)=>en?english:zh;
  const {session}=useAuth();const can=(p:string)=>hasPermission(session?.currentRole,session?.permissionCodes,p);
  const {message}=App.useApp();const client=useQueryClient();const [page,setPage]=useState(0);const [selected,setSelected]=useState<CommercialDocument>();
  const [edit,setEdit]=useState<CommercialDocument>();const [formOpen,setFormOpen]=useState(false);const [profileOpen,setProfileOpen]=useState(false);const [busy,setBusy]=useState(false);
  const [key,setKey]=useState('');const [form]=Form.useForm<QuoteInput>();const [profileForm]=Form.useForm<CompanyProfile>();
  const list=useQuery({queryKey:['commercial','quotes',page],queryFn:()=>listDocuments('quotes',page)});
  const profile=useQuery({queryKey:['commercial','profile'],queryFn:getCompanyProfile});
  const customers=useQuery({queryKey:['customers','active','CLIENT'],queryFn:()=>listCustomers(true,'CLIENT'),enabled:formOpen});
  const products=useQuery({queryKey:['product-skus','active'],queryFn:()=>listProductSkus({enabledOnly:true}),enabled:formOpen});
  const changed=(d:CommercialDocument)=>{setSelected(d);void client.invalidateQueries({queryKey:['commercial','quotes']});};
  const openForm=(d?:CommercialDocument)=>{setEdit(d);setKey(crypto.randomUUID());form.resetFields();form.setFieldsValue(d?{customerId:d.customerId,version:d.version,validUntil:d.dueDate,shippingAddress:d.shippingAddress?.includes('***')?undefined:d.shippingAddress,terms:d.terms,lines:d.lines}:{validUntil:localDateAfter(30),terms:profile.data?.terms,lines:[{quantity:1,unitPrice:0}]});setFormOpen(true);};
  async function save(){const input=await form.validateFields();setBusy(true);try{changed(await saveQuote({...input,requestKey:key,version:edit?.version},edit?.id));setFormOpen(false);}catch(e){message.error(getErrorMessage(e,t));}finally{setBusy(false);}}
  async function convert(d:CommercialDocument){setBusy(true);try{changed(await convertQuote(d.id));void client.invalidateQueries({queryKey:['sales-orders']});message.success(L('已转为销售订单，按现有审批和库存规则处理。','Converted using the existing order approval and stock rules.'));}catch(e){message.error(getErrorMessage(e,t));}finally{setBusy(false);}}
  return <CommercialSurface><Card title={L('报价','Quotations')} extra={<Space>{session?.currentRole==='TENANT_ADMIN' && <Button onClick={()=>{profileForm.setFieldsValue(profile.data??{name:''});setProfileOpen(true);}}>{L('公司抬头与默认条款','Company details and terms')}</Button>}{can('quotation:edit') && <Button type="primary" onClick={()=>openForm()}>{L('新建报价','New quote')}</Button>}</Space>}>
    <p>{L('报价不预留库存。客户确认后转销售订单，也可直接到销售模块下单。','Quotes do not reserve stock. Convert when confirmed, or create a sales order directly.')}</p>
    {list.error && <Alert type="error" message={getErrorMessage(list.error,t)} />}
    <Table rowKey="id" loading={list.isLoading||busy} dataSource={list.data?.content??[]} pagination={{current:page+1,pageSize:20,total:list.data?.totalElements,onChange:p=>setPage(p-1),showSizeChanger:false}} columns={[
      {title:L('编号','Number'),dataIndex:'number',render:(value:string,d:CommercialDocument)=><Button type="link" onClick={()=>setSelected(d)}>{value}</Button>},
      {title:L('客户','Customer'),dataIndex:'customerName'}, {title:L('有效期','Valid until'),dataIndex:'dueDate'},
      {title:'GBP',dataIndex:'total',render:(n:number)=>n.toFixed(2)}, {title:L('状态','Status'),render:(_,d)=><Tag>{documentStatus(d,en)}</Tag>}, {title:L('关联订单','Sales order'),dataIndex:'salesOrderNo'},
    ]}/>
  </Card>
  <DocumentDetails document={selected} onClose={()=>setSelected(undefined)} onChanged={changed} onEdit={openForm} onConvert={d=>void convert(d)}/>
  <Drawer open={formOpen} width={960} title={L(edit?'编辑报价':'新建报价',edit?'Edit quote':'New quote')} onClose={()=>!busy&&setFormOpen(false)} destroyOnHidden extra={<Button type="primary" loading={busy} onClick={()=>void save()}>{L('保存报价','Save quote')}</Button>}>
    {(products.error||customers.error) && <Alert type="error" message={getErrorMessage(products.error??customers.error,t)}/>}
    <Form form={form} layout="vertical">
      <Space align="start"><Form.Item name="customerId" label={L('客户','Customer')} rules={[{required:true}]}><Select style={{width:330}} disabled={!!edit} showSearch optionFilterProp="label" options={(customers.data??[]).map(c=>({value:c.id,label:`${c.code} · ${c.name}`}))} onChange={id=>{const c=customers.data?.find(x=>x.id===id);const address=c?.shippingSameAsBilling===false?c.shippingAddress:c?.address;form.setFieldValue('shippingAddress',address?.includes('***')?undefined:address);}}/></Form.Item>
      <Form.Item name="validUntil" label={L('有效期','Valid until')} rules={[{required:true}]}><Input type="date" min={localDateAfter()}/></Form.Item></Space>
      <Form.Item name="shippingAddress" label={L('本次收货地址（留空沿用客户地址）','Shipping address (defaults to customer address)')}><Input maxLength={255}/></Form.Item>
      <Form.List name="lines" rules={[{validator:async(_,v)=>{if(!v?.length)throw new Error(L('至少添加一个商品','Add at least one product'));}}]}>{(fields,{add,remove},{errors})=><>
        {fields.map(field=><Space key={field.key} align="start" wrap>
          <Form.Item name={[field.name,'productSkuId']} label={L('商品','Product')} rules={[{required:true}]}><Select style={{width:310}} showSearch optionFilterProp="label" options={(products.data??[]).map(p=>({value:p.id,label:`${p.skuCode} · ${p.name} / ${p.baseUnit??'件'}`}))} onChange={id=>{const p=products.data?.find(x=>x.id===id);form.setFieldValue(['lines',field.name,'unitPrice'],p?.unitPrice??0);}}/></Form.Item>
          <Form.Item name={[field.name,'quantity']} label={L('数量（基础单位）','Quantity (base unit)')} rules={[{required:true}]}><InputNumber min={1} max={1000000} precision={0}/></Form.Item>
          <Form.Item name={[field.name,'unitPrice']} label={L('单价 GBP','Unit price GBP')} rules={[{required:true}]}><InputNumber min={0} max={99999999.99} precision={2}/></Form.Item>
          <Button style={{marginTop:30}} danger onClick={()=>remove(field.name)}>{L('移除','Remove')}</Button>
        </Space>)}
        <Form.ErrorList errors={errors}/><Button disabled={fields.length>=200} onClick={()=>add({quantity:1,unitPrice:0})}>{L('添加商品','Add product')}</Button>
      </>}</Form.List>
      <p>{L('币种 GBP，商品单价合计；未另行计算税费或运费。','Prices in GBP; no additional tax or freight calculated.')}</p>
      <Form.Item name="terms" label={L('条款','Terms')}><Input.TextArea rows={4} maxLength={2000} showCount/></Form.Item>
    </Form>
  </Drawer>
  <Modal open={profileOpen} title={L('公司抬头与默认条款','Company details and terms')} confirmLoading={busy} onCancel={()=>!busy&&setProfileOpen(false)} onOk={()=>void profileForm.validateFields().then(async v=>{setBusy(true);try{await saveCompanyProfile(v);await profile.refetch();setProfileOpen(false);}catch(e){message.error(getErrorMessage(e,t));}finally{setBusy(false);}})}>
    <Form form={profileForm} layout="vertical"><Form.Item name="name" label={L('公司名称','Company name')} rules={[{required:true,whitespace:true}]}><Input maxLength={200}/></Form.Item>
      <Form.Item name="address" label={L('地址','Address')}><Input maxLength={255}/></Form.Item><Form.Item name="phone" label={L('电话','Phone')}><Input maxLength={50}/></Form.Item>
      <Form.Item name="email" label={L('邮箱','Email')} rules={[{type:'email'}]}><Input maxLength={100}/></Form.Item><Form.Item name="terms" label={L('默认条款','Default terms')}><Input.TextArea rows={4} maxLength={2000}/></Form.Item>
    </Form>
  </Modal></CommercialSurface>;
}
