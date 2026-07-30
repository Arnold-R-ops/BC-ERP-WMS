import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

vi.mock('../../api/stocktake', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/stocktake')>(),
  getStocktakeTask: vi.fn(),
  listStocktakeItems: vi.fn(),
  listStocktakeReviewItems: vi.fn(),
}));

import {
  getStocktakeTask,
  listStocktakeItems,
  listStocktakeReviewItems,
} from '../../api/stocktake';
import { StocktakeTaskDrawer } from './StocktakeTaskDrawer';

function renderDrawer(canCount: boolean, canReview: boolean): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <ConfigProvider>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <StocktakeTaskDrawer
            canCount={canCount}
            canReview={canReview}
            onClose={() => undefined}
            onCount={() => undefined}
            onReview={() => undefined}
            onStart={() => undefined}
            open
            taskId={8}
          />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  );
}

describe('stocktake item visibility boundary', () => {
  afterEach(() => cleanup());

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en-US');
    vi.mocked(listStocktakeItems).mockResolvedValue([{
      id: 13,
      taskId: 8,
      productName: 'Jasmine tea',
      batchCode: 'BATCH-001',
      locationCode: 'WH-A-01',
      isCounted: false,
    }]);
    vi.mocked(listStocktakeReviewItems).mockResolvedValue([]);
  });

  it('uses the blind item endpoint while a counter is working', async () => {
    vi.mocked(getStocktakeTask).mockResolvedValue({
      id: 8,
      taskNo: 'ST20260722001',
      status: 'COUNTING',
    });

    renderDrawer(true, false);

    expect(await screen.findByText(/Blind count is active/)).toBeVisible();
    await waitFor(() => expect(listStocktakeItems).toHaveBeenCalledWith(8));
    expect(listStocktakeReviewItems).not.toHaveBeenCalled();
    expect(screen.queryByText('Book snapshot')).not.toBeInTheDocument();
  });

  it('uses the review endpoint only after the task enters review', async () => {
    vi.mocked(getStocktakeTask).mockResolvedValue({
      id: 8,
      taskNo: 'ST20260722001',
      status: 'REVIEWING',
    });

    renderDrawer(false, true);

    await waitFor(() => expect(listStocktakeReviewItems).toHaveBeenCalledWith(8));
    expect(listStocktakeItems).not.toHaveBeenCalled();
    expect((await screen.findAllByText('Book snapshot')).length).toBeGreaterThan(0);
  });
});
