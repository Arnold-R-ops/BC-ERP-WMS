import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {App as AntdApp, ConfigProvider} from 'antd';
import zhCN from 'antd/locale/zh_CN';
import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest';
import i18n from '../../locales/i18n';
import type {CommercialDocument} from '../../api/commercial';
const auth=vi.hoisted(()=>({role:'TENANT_ADMIN',permissions:[] as string[]}));
vi.mock('../../auth/AuthProvider',()=>({useAuth:()=>({session:{currentRole:auth.role,permissionCodes:auth.permissions}})}));
vi.mock('../../api/commercial',async original=>({...await original<typeof import('../../api/commercial')>(),recordReceipt:vi.fn(),reverseReceipt:vi.fn(),exportDocument:vi.fn()}));
import {recordReceipt,reverseReceipt} from '../../api/commercial';
import {DocumentDetails,documentStatus} from './DocumentDetails';
const invoice:CommercialDocument={id:1,version:0,number:'INV-000001',kind:'INVOICE',status:'ISSUED',customerId:1,customerName:'测试客户',company:{name:'测试公司'},documentDate:'2026-09-19',dueDate:'2026-10-19',currency:'GBP',total:50,received:10,outstanding:40,orderAmountChanged:false,lines:[],receipts:[{id:1,kind:'RECEIPT',amount:10,paymentDate:'2026-09-19',method:'BANK_TRANSFER',createdBy:1,createdAt:'2026-09-19'}]};
function show(d=invoice){const changed=vi.fn();render(<ConfigProvider locale={zhCN}><AntdApp><DocumentDetails document={d} onChanged={changed} onClose={vi.fn()}/></AntdApp></ConfigProvider>);return changed;}
describe('invoice controls',()=>{
  beforeEach(async()=>{vi.clearAllMocks();auth.role='TENANT_ADMIN';auth.permissions=[];await i18n.changeLanguage('zh-CN');});afterEach(cleanup);
  it('retries a receipt with the same request key',async()=>{
    vi.mocked(recordReceipt).mockRejectedValueOnce(new Error('temporary failure')).mockResolvedValue({...invoice,received:50,outstanding:0});const changed=show();
    fireEvent.click(screen.getByRole('button',{name:'登记收款'}));
    fireEvent.click(await screen.findByRole('button',{name:/确.*定/}));
    await waitFor(()=>expect(recordReceipt).toHaveBeenCalledTimes(1));
    await screen.findByText('temporary failure');
    fireEvent.click(screen.getByRole('button',{name:/确.*定/}));
    await waitFor(()=>expect(changed).toHaveBeenCalled());
    const first=vi.mocked(recordReceipt).mock.calls[0][1];const second=vi.mocked(recordReceipt).mock.calls[1][1];
    expect(first.amount).toBe(40);expect(second.requestKey).toBe(first.requestKey);expect(first.requestKey).toMatch(/^[\da-f-]{36}$/);
  });
  it('does not offer payment, reversal or customer exports to a viewer',()=>{
    auth.role='CUSTOM_ROLE';auth.permissions=['invoice:view'];show();
    expect(screen.queryByRole('button',{name:'登记收款'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'更正录入'})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:'PDF'})).not.toBeInTheDocument();
  });
  it('requires a reason before reversing a mistaken receipt',async()=>{
    vi.mocked(reverseReceipt).mockResolvedValue({...invoice,received:0,outstanding:50});show();
    fireEvent.click(screen.getByRole('button',{name:'更正录入'}));
    const confirm=await screen.findByRole('button',{name:/确.*定/});expect(confirm).toBeDisabled();
    fireEvent.change(screen.getByLabelText('原因'),{target:{value:'重复录入'}});fireEvent.click(confirm);
    await waitFor(()=>expect(reverseReceipt).toHaveBeenCalledWith(1,1,'重复录入'));
  });
  it('separates commercial payment status from dispatch',()=>{expect(documentStatus(invoice)).toBe('部分收款');expect(documentStatus({...invoice,outstanding:0,received:50})).toBe('已收清');});
});
