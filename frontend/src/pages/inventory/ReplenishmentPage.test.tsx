import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {App as AntdApp} from 'antd';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest';
import i18n from '../../locales/i18n';
const captured=vi.hoisted(()=>({initial:undefined as unknown,role:'TENANT_ADMIN'}));
vi.mock('../../auth/AuthProvider',()=>({useAuth:()=>({session:{currentRole:captured.role,permissionCodes:['menu:inventory','inventory:view']}})}));
vi.mock('../../api/replenishment',()=>({listReplenishment:vi.fn(),listReplenishmentWarehouses:vi.fn(),saveWarehouseMinimum:vi.fn()}));
vi.mock('../../api/csv',()=>({downloadCsv:vi.fn()}));
vi.mock('../purchasing/PurchaseOrderFormDrawer',()=>({PurchaseOrderFormDrawer:(props:{initialValues:unknown})=>{captured.initial=props.initialValues;return null;}}));
import {listReplenishment,listReplenishmentWarehouses,saveWarehouseMinimum} from '../../api/replenishment';
import {downloadCsv} from '../../api/csv';
import {ReplenishmentPage} from './ReplenishmentPage';
function show(){render(<MemoryRouter><AntdApp><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ReplenishmentPage/></QueryClientProvider></AntdApp></MemoryRouter>);}
describe('replenishment workflow',()=>{
  beforeEach(async()=>{vi.clearAllMocks();captured.initial=undefined;captured.role='TENANT_ADMIN';await i18n.changeLanguage('zh-CN');
    vi.mocked(listReplenishmentWarehouses).mockResolvedValue([{id:2,name:'主仓'}]);
    vi.mocked(listReplenishment).mockResolvedValue([{productSkuId:1,productName:'茉莉花茶',barcode:'TEA',baseUnit:'袋',currentStock:2,outstandingPurchaseQuantity:3,targetStock:10,minStock:10,thresholdConfigured:true,suggestedReorderQuantity:5},{productSkuId:2,productName:'红茶',barcode:'BLACK',baseUnit:'袋',currentStock:3,outstandingPurchaseQuantity:0,targetStock:10,minStock:10,thresholdConfigured:true,suggestedReorderQuantity:7}]);
  });afterEach(cleanup);
  it('carries only selected net quantities and the warehouse to the existing purchase form',async()=>{
    show();await screen.findByText('茉莉花茶');await waitFor(()=>expect(listReplenishment).toHaveBeenCalledWith(2,false));
    fireEvent.click(screen.getAllByRole('checkbox')[1]);fireEvent.click(screen.getByRole('button',{name:'生成采购草稿'}));
    expect(captured.initial).toEqual({items:[{productSkuId:1,orderedQuantity:5,allocations:[{warehouseId:2,plannedQuantity:5}]}]});
  });
  it('exports exactly the filtered rows with their base unit and incoming quantity',async()=>{
    show();await screen.findByText('茉莉花茶');await waitFor(()=>expect(listReplenishment).toHaveBeenCalledWith(2,false));fireEvent.change(screen.getByPlaceholderText('搜索商品、条码、供应商'),{target:{value:'茉莉'}});
    fireEvent.click(screen.getByRole('button',{name:'导出 CSV'}));
    expect(downloadCsv).toHaveBeenCalledOnce();const rows=vi.mocked(downloadCsv).mock.calls[0][1];expect(rows).toHaveLength(2);expect(rows[1]).toEqual(expect.arrayContaining(['主仓','TEA','茉莉花茶','袋',2,3,5]));
  });
  it('hides purchase and threshold editing without their permissions',async()=>{
    captured.role='VIEWER';show();await screen.findByText('茉莉花茶');expect(screen.queryByRole('button',{name:'生成采购草稿'})).not.toBeInTheDocument();expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument();expect(saveWarehouseMinimum).not.toHaveBeenCalled();
  });
});
