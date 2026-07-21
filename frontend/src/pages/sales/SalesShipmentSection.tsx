import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { ModalForm, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Popconfirm, Space, Table, Tag, Tooltip, Typography, type TableColumnsType } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  createSalesOrderShipment,
  listSalesOrderShipments,
  SALES_SHIPMENTS_QUERY_KEY,
  updateSalesOrderShipment,
  voidSalesOrderShipment,
  type SalesOrderShipment,
  type SalesOrderShipmentPayload,
} from '../../api/sales';
import { formatDateTime } from '../workflowUtils';

interface SalesShipmentSectionProps {
  canEdit: boolean;
  salesOrderId: number;
}

export function SalesShipmentSection({ canEdit, salesOrderId }: SalesShipmentSectionProps): JSX.Element {
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<SalesOrderShipment>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const queryKey = [...SALES_SHIPMENTS_QUERY_KEY, salesOrderId];
  const shipmentsQuery = useQuery({ queryKey, queryFn: () => listSalesOrderShipments(salesOrderId) });
  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id?: number; payload: SalesOrderShipmentPayload }) =>
      id === undefined
        ? createSalesOrderShipment(salesOrderId, payload)
        : updateSalesOrderShipment(salesOrderId, id, payload),
  });
  const voidMutation = useMutation({ mutationFn: (id: number) => voidSalesOrderShipment(salesOrderId, id) });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey });
  };

  const save = async (payload: SalesOrderShipmentPayload): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editing?.id, payload });
      message.success(t(editing ? 'sales.shipments.messages.updated' : 'sales.shipments.messages.created'));
      setFormOpen(false);
      setEditing(undefined);
      await refresh();
      return true;
    } catch (error) { message.error(getErrorMessage(error, t)); return false; }
  };

  const voidShipment = async (shipment: SalesOrderShipment): Promise<void> => {
    if (shipment.id === undefined) return;
    try { await voidMutation.mutateAsync(shipment.id); message.success(t('sales.shipments.messages.voided')); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const columns: TableColumnsType<SalesOrderShipment> = [
    { title: t('sales.shipments.fields.trackingNo'), dataIndex: 'trackingNo', width: 190 },
    { title: t('sales.shipments.fields.carrier'), dataIndex: 'carrier', width: 140 },
    { title: t('sales.shipments.fields.status'), dataIndex: 'status', width: 110, render: (value) => <Tag color={value === 'VOIDED' ? 'default' : 'success'}>{t(`sales.shipments.statuses.${String(value ?? 'ACTIVE')}`)}</Tag> },
    { title: t('sales.shipments.fields.shippedAt'), dataIndex: 'shippedAt', width: 180, render: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('sales.shipments.fields.createdBy'), dataIndex: 'createdByName', width: 130 },
    { title: t('common.remark'), dataIndex: 'remark', width: 220 },
    ...(canEdit ? [{
      title: t('common.actions'), width: 100, fixed: 'right' as const,
      render: (_: unknown, row: SalesOrderShipment) => row.status === 'VOIDED' ? null : (
        <Space size={2}>
          <Tooltip title={t('common.edit')}><Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditing(row); setFormOpen(true); }} size="small" type="text" /></Tooltip>
          <Popconfirm onConfirm={() => void voidShipment(row)} title={t('sales.shipments.confirmVoid')}><Tooltip title={t('sales.shipments.actions.void')}><Button aria-label={t('sales.shipments.actions.void')} danger icon={<DeleteOutlined />} loading={voidMutation.isPending} size="small" type="text" /></Tooltip></Popconfirm>
        </Space>
      ),
    }] : []),
  ];

  return (
    <section className="workflow-subsection">
      <div className="workflow-subsection-heading">
        <Typography.Title level={5}>{t('sales.shipments.title')}</Typography.Title>
        {canEdit && <Button icon={<PlusOutlined />} onClick={() => { setEditing(undefined); setFormOpen(true); }} size="small">{t('sales.shipments.actions.create')}</Button>}
      </div>
      <Table<SalesOrderShipment>
        columns={columns}
        dataSource={shipmentsQuery.data ?? []}
        loading={shipmentsQuery.isLoading}
        pagination={false}
        rowClassName={(row) => row.status === 'VOIDED' ? 'shipment-row-voided' : ''}
        rowKey={(row) => row.id ?? row.trackingNo ?? 'shipment'}
        scroll={{ x: 950 }}
        size="small"
      />

      <ModalForm<SalesOrderShipmentPayload>
        key={editing?.id ?? 'new-shipment'}
        initialValues={{ trackingNo: editing?.trackingNo ?? '', carrier: editing?.carrier ?? '', trackingUrl: editing?.trackingUrl ?? '', shippedAt: editing?.shippedAt ?? '', remark: editing?.remark ?? '' }}
        modalProps={{ destroyOnHidden: true, maskClosable: false }}
        onFinish={save}
        onOpenChange={(nextOpen) => { if (!nextOpen) { setFormOpen(false); setEditing(undefined); } }}
        open={formOpen}
        submitter={{ searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') }, submitButtonProps: { loading: saveMutation.isPending } }}
        title={editing ? t('sales.shipments.form.editTitle') : t('sales.shipments.form.createTitle')}
        width={620}
      >
        <ProFormText fieldProps={{ maxLength: 100 }} label={t('sales.shipments.fields.trackingNo')} name="trackingNo" rules={[{ required: true, message: t('sales.shipments.validation.trackingRequired') }]} />
        <ProFormText fieldProps={{ maxLength: 100 }} label={t('sales.shipments.fields.carrier')} name="carrier" />
        <ProFormText fieldProps={{ maxLength: 500 }} label={t('sales.shipments.fields.trackingUrl')} name="trackingUrl" />
        <ProFormText fieldProps={{ type: 'datetime-local' }} label={t('sales.shipments.fields.shippedAt')} name="shippedAt" />
        <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('common.remark')} name="remark" />
      </ModalForm>
    </section>
  );
}
