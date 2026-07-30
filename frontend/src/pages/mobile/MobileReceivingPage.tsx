import { CheckCircleFilled, InboxOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Empty, InputNumber, Progress, Select, Skeleton, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  INBOUND_ORDERS_QUERY_KEY,
  listInboundOrders,
  receiveInboundOrder,
  type InboundOrder,
  type InboundReceiptPayload,
} from '../../api/inbound';
import { ScanInput } from './ScanInput';
import { WarehouseOperationSteps } from './WarehouseOperationSteps';
import { useMobileOperation } from './WarehouseMobileLayout';

interface ReceiptDraft {
  actualQty: number;
  locationId: number;
  productVerified: boolean;
  locationVerified: boolean;
}

export function MobileReceivingPage(): JSX.Element {
  const [selectedOrderId, setSelectedOrderId] = useState<number>();
  const [activeItemId, setActiveItemId] = useState<number>();
  const [drafts, setDrafts] = useState<Record<number, ReceiptDraft>>({});
  const { online } = useMobileOperation();
  const { message } = AntdApp.useApp();
  const { t } = useTranslation();

  const ordersQuery = useQuery({
    queryKey: [...INBOUND_ORDERS_QUERY_KEY, 'AWAITING_RECEIVAL', 'mobile'],
    queryFn: () => listInboundOrders('AWAITING_RECEIVAL'),
  });
  const orders = ordersQuery.data ?? [];
  const selectedOrder = orders.find((order) => order.id === selectedOrderId);

  useEffect(() => {
    if (selectedOrderId !== undefined && !selectedOrder) setSelectedOrderId(undefined);
  }, [selectedOrder, selectedOrderId]);

  useEffect(() => {
    if (!selectedOrder) {
      setDrafts({});
      setActiveItemId(undefined);
      return;
    }
    const next = Object.fromEntries((selectedOrder.items ?? [])
      .filter((item) => item.id !== undefined && item.targetLocationId !== undefined)
      .map((item) => [item.id as number, {
        actualQty: item.confirmedQty ?? item.planQty ?? 0,
        locationId: item.targetLocationId as number,
        productVerified: false,
        locationVerified: false,
      }]));
    setDrafts(next);
    setActiveItemId(undefined);
  }, [selectedOrder?.id]);

  const receiveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: InboundReceiptPayload }) => receiveInboundOrder(id, payload),
  });

  const readyCount = Object.values(drafts).filter((draft) => draft.productVerified && draft.locationVerified).length;
  const totalCount = selectedOrder?.items?.length ?? 0;
  const readyPercent = totalCount === 0 ? 0 : Math.round((readyCount / totalCount) * 100);

  const orderOptions = useMemo(() => orders
    .filter((order) => order.id !== undefined)
    .map((order) => ({
      label: `${order.orderNo ?? `#${order.id}`} · ${order.supplierName ?? '-'}`,
      value: order.id as number,
    })), [orders]);

  const scanProduct = (code: string): void => {
    const normalized = code.toLowerCase();
    const matches = (selectedOrder?.items ?? []).filter((item) => [item.productBarcode, item.batchCode, item.externalBatchCode]
      .some((value) => value?.toLowerCase() === normalized));
    const item = matches.find((candidate) => candidate.id !== undefined && !drafts[candidate.id]?.productVerified) ?? matches[0];
    if (!item?.id) {
      message.error(t('mobile.receiving.scanNotFound'));
      return;
    }
    setActiveItemId(item.id);
    setDrafts((current) => ({ ...current, [item.id as number]: { ...current[item.id as number], productVerified: true } }));
    message.success(t('mobile.receiving.productVerified', { name: item.productName ?? '-' }));
  };

  const scanLocation = (code: string): void => {
    const item = selectedOrder?.items?.find((candidate) => candidate.id === activeItemId);
    if (!item?.id || code.toLowerCase() !== item.targetLocationCode?.toLowerCase()) {
      message.error(t('mobile.receiving.locationMismatch', { location: item?.targetLocationCode ?? '-' }));
      return;
    }
    setDrafts((current) => ({ ...current, [item.id as number]: { ...current[item.id as number], locationVerified: true } }));
    message.success(t('mobile.receiving.locationVerified'));
  };

  const submit = async (): Promise<void> => {
    if (!selectedOrder?.id || !online) return;
    const items = selectedOrder.items ?? [];
    if (items.length === 0 || items.some((item) => !item.id || !drafts[item.id]?.productVerified || !drafts[item.id]?.locationVerified)) {
      message.warning(t('mobile.receiving.verifyAll'));
      return;
    }
    const payload: InboundReceiptPayload = {
      receipts: items.map((item) => ({
        itemId: item.id as number,
        actualQty: drafts[item.id as number].actualQty,
        locationId: drafts[item.id as number].locationId,
      })),
    };
    try {
      await receiveMutation.mutateAsync({ id: selectedOrder.id, payload });
      message.success(t('mobile.receiving.completed'));
      setSelectedOrderId(undefined);
      await ordersQuery.refetch();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  return (
    <section className="mobile-operation-page">
      <header className="mobile-operation-heading">
        <div><Typography.Title level={2}>{t('mobile.receiving.title')}</Typography.Title><p>{t('mobile.receiving.subtitle')}</p></div>
        <Button aria-label={t('common.refresh')} disabled={!online} icon={<ReloadOutlined spin={ordersQuery.isFetching} />} onClick={() => void ordersQuery.refetch()} />
      </header>

      {ordersQuery.isLoading ? <Skeleton active /> : orders.length === 0 ? (
        <Empty description={t('mobile.receiving.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <>
          <WarehouseOperationSteps current={!selectedOrder ? 0 : readyCount === totalCount && totalCount > 0 ? 2 : 1} />
          <div className="mobile-operation-workspace">
            <section className="mobile-operation-panel">
              <label className="mobile-field-label">
                <span>{t('mobile.receiving.order')}</span>
                <Select
                  allowClear
                  onChange={setSelectedOrderId}
                  optionFilterProp="label"
                  options={orderOptions}
                  placeholder={t('mobile.receiving.selectOrder')}
                  showSearch
                  value={selectedOrderId}
                />
              </label>
              {!selectedOrder ? (
                <div className="mobile-selection-hint">{t('mobile.receiving.selectOrderHint')}</div>
              ) : (
                <>
                  <div className="mobile-progress-band"><span>{t('mobile.receiving.progress', { ready: readyCount, total: totalCount })}</span><Progress percent={readyPercent} showInfo={false} /></div>
                  <ScanInput autoFocus disabled={!online} label={t('mobile.receiving.scanProduct')} onScan={scanProduct} placeholder={t('mobile.receiving.scanProductPlaceholder')} />
                  <ScanInput
                    disabled={!activeItemId || !online}
                    label={t('mobile.receiving.scanLocation')}
                    manualValue={selectedOrder.items?.find((item) => item.id === activeItemId)?.targetLocationCode}
                    onScan={scanLocation}
                    placeholder={t('mobile.receiving.scanLocationPlaceholder')}
                  />
                  <Button block disabled={!online || readyCount !== totalCount || totalCount === 0} loading={receiveMutation.isPending} onClick={() => void submit()} size="large" type="primary">
                    {t('mobile.receiving.submit')}
                  </Button>
                </>
              )}
            </section>

            <aside className="mobile-work-queue">
              <div className="mobile-work-queue-heading">
                <strong>{t('mobile.workQueue')}</strong>
                <span>{selectedOrder ? t('mobile.receiving.progress', { ready: readyCount, total: totalCount }) : t('mobile.selectTaskFirst')}</span>
              </div>
              {!selectedOrder ? <Empty description={t('mobile.selectTaskFirst')} image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
                <div className="mobile-task-list">
                  {(selectedOrder.items ?? []).map((item) => {
                    const draft = item.id ? drafts[item.id] : undefined;
                    const ready = draft?.productVerified && draft.locationVerified;
                    return (
                      <article className={`mobile-task-card${item.id === activeItemId ? ' is-active' : ''}${ready ? ' is-ready' : ''}`} key={item.id} onClick={() => setActiveItemId(item.id)}>
                        <div className="mobile-task-card-top"><strong>{item.productName ?? '-'}</strong>{ready ? <CheckCircleFilled /> : <Tag>{t('mobile.pendingScan')}</Tag>}</div>
                        <span>{item.productBarcode ?? '-'}</span>
                        <div className="mobile-task-primary"><InboxOutlined /> {item.targetLocationCode ?? '-'}</div>
                        <div className="mobile-task-meta"><span>{t('mobile.batch')} {item.batchCode ?? '-'}</span><span>{t('mobile.receiving.expected')} {item.confirmedQty ?? item.planQty ?? 0}</span></div>
                        <label className="mobile-inline-quantity" onClick={(event) => event.stopPropagation()}>
                          <span>{t('mobile.receiving.actualQty')}</span>
                          <InputNumber
                            disabled={!online}
                            max={item.confirmedQty ?? item.planQty ?? 0}
                            min={0}
                            onChange={(value) => item.id && setDrafts((current) => ({ ...current, [item.id as number]: { ...current[item.id as number], actualQty: Number(value ?? 0) } }))}
                            precision={0}
                            value={draft?.actualQty}
                          />
                        </label>
                      </article>
                    );
                  })}
                </div>
              )}
            </aside>
          </div>
        </>
      )}
    </section>
  );
}
