import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';
import type { StocktakeItem } from '../../api/stocktake';

const state = vi.hoisted(() => ({ online: true }));
vi.mock('./WarehouseMobileLayout', () => ({ useMobileOperation: () => state }));
vi.mock('./ScanInput', () => ({
  ScanInput: ({ onScan, disabled }: { onScan: (code: string) => void; disabled?: boolean }) =>
    <input aria-label="Test batch scan" disabled={disabled} onKeyDown={(event) => {
      if (event.key === 'Enter') onScan(event.currentTarget.value);
    }} />,
}));
vi.mock('../../api/stocktake', async (original) => ({
  ...await original<typeof import('../../api/stocktake')>(),
  listStocktakeTasks: vi.fn(),
  listStocktakeItems: vi.fn(),
  submitStocktakeCount: vi.fn(),
  finishStocktake: vi.fn(),
}));
import { finishStocktake, listStocktakeItems, listStocktakeTasks, submitStocktakeCount } from '../../api/stocktake';
import { MobileStocktakePage } from './MobileStocktakePage';

let items: StocktakeItem[];
async function openTask(): Promise<void> {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<AntdApp><QueryClientProvider client={client}><MobileStocktakePage /></QueryClientProvider></AntdApp>);
  fireEvent.mouseDown(await screen.findByRole('combobox'));
  fireEvent.click(await screen.findByText('TK-FIX / Warehouse A'));
  await screen.findByRole('button', { name: /L-1.*Tea/ });
}
function scan(code: string): void {
  const input = screen.getByRole('textbox', { name: 'Test batch scan' });
  fireEvent.change(input, { target: { value: code } });
  fireEvent.keyDown(input, { key: 'Enter' });
}

describe('mobile stocktake correction and explicit finish', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    state.online = true;
    await i18n.changeLanguage('en-US');
    items = [
      { id: 11, taskId: 8, productName: 'Tea', locationCode: 'L-1', batchCode: 'B-1', countedQty: 3, isCounted: true },
      { id: 12, taskId: 8, productName: 'Rice', locationCode: 'L-2', batchCode: 'B-2', countedQty: 4, isCounted: true },
    ];
    vi.mocked(listStocktakeTasks).mockResolvedValue([{ id: 8, taskNo: 'TK-FIX', warehouseName: 'Warehouse A', status: 'COUNTING' }]);
    vi.mocked(listStocktakeItems).mockImplementation(async () => items.map((item) => ({ ...item })));
    vi.mocked(submitStocktakeCount).mockImplementation(async (_task, id, payload) => {
      items = items.map((item) => item.id === id ? { ...item, ...payload, isCounted: true } : item);
      return items.find((item) => item.id === id)!;
    });
    vi.mocked(finishStocktake).mockImplementation(async () => {
      vi.mocked(listStocktakeTasks).mockResolvedValue([]);
      return { id: 8, status: 'REVIEWING' };
    });
  });
  afterEach(cleanup);

  it('corrects multiple saved lines by scan/card and submits only after explicit confirmation', async () => {
    await openTask();
    scan('B-1');
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '5' } });
    expect(screen.getByRole('button', { name: 'Finish stocktake count' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Save line count' }));
    await waitFor(() => expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument());
    expect(submitStocktakeCount).toHaveBeenLastCalledWith(8, 11, { countedQty: 5, remark: undefined });
    expect(finishStocktake).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /L-2.*Rice/ }));
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save line count' }));
    await waitFor(() => expect(submitStocktakeCount).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument());
    expect(finishStocktake).not.toHaveBeenCalled();
    expect(screen.queryByText('Book snapshot')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Finish stocktake count' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getAllByText('Submit all count results?').length).toBeGreaterThan(0);
    expect(finishStocktake).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Finish stocktake count' }));
    await waitFor(() => expect(finishStocktake).toHaveBeenCalledOnce());
    expect(vi.mocked(finishStocktake).mock.calls[0][0]).toBe(8);
  });

  it('retains an unsaved correction when saving fails and prevents finish', async () => {
    vi.mocked(submitStocktakeCount).mockRejectedValue(new Error('Temporary failure'));
    await openTask();
    scan('B-1');
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save line count' }));
    await waitFor(() => expect(submitStocktakeCount).toHaveBeenCalledOnce());
    await waitFor(() => expect(screen.getByRole('button', { name: 'Save line count' })).toBeEnabled());
    expect(screen.getByRole('spinbutton')).toHaveValue('7');
    expect(screen.getByRole('button', { name: 'Finish stocktake count' })).toBeDisabled();
    expect(finishStocktake).not.toHaveBeenCalled();
  });

  it('prevents finish if any item has not been counted', async () => {
    items[1] = { ...items[1], isCounted: false, countedQty: undefined };
    await openTask();
    expect(screen.getByRole('button', { name: 'Finish stocktake count' })).toBeDisabled();
    expect(finishStocktake).not.toHaveBeenCalled();
  });

  it('blocks count and finish controls when offline', async () => {
    state.online = false;
    await openTask();
    expect(screen.getByRole('button', { name: /L-1.*Tea/ })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: 'Test batch scan' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Finish stocktake count' })).toBeDisabled();
    expect(submitStocktakeCount).not.toHaveBeenCalled();
    expect(finishStocktake).not.toHaveBeenCalled();
  });
});
