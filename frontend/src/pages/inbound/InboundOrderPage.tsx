import { CheckOutlined, EditOutlined, EyeOutlined, PlusOutlined, SettingOutlined, StopOutlined } from '@ant-design/icons';
import { DrawerForm, ProFormSelect, ProFormText, ProFormTextArea, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntdApp, Button, Descriptions, Drawer, Form, Input, InputNumber, Popconfirm, Select, Space, Table, Tag, type TableColumnsType } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { getErrorMessage } from '../../api/errors';
import {
  createOtherInboundApplication,
  getOtherInboundApplication,
  getOtherInboundPolicy,
  listOtherInboundApplications,
  listOtherInboundReferenceSkus,
  listOtherInboundReferenceWarehouses,
  OTHER_INBOUND_QUERY_KEY,
  runOtherInboundAction,
  updateOtherInboundApplication,
  updateOtherInboundPolicy,
  type OtherInboundApplication,
  type OtherInboundApplicationItem,
  type OtherInboundApplicationPayload,
  type OtherInboundPolicyUpdatePayload,
} from '../../api/inbound';
import { paginateArray } from '../../api/pagination';
import { useAuth } from '../../auth/AuthProvider';
import { ReasonModal } from '../../components/ReasonModal';
import { formatDate, formatDateTime } from '../workflowUtils';
import { OtherInboundPolicyModal } from './OtherInboundPolicyModal';

interface TableParams { current?: number; pageSize?: number; search?: string }
type OtherInboundReasonAction = 'cancel' | 'void' | 'close';
export type OtherInboundUiAction = 'edit' | 'confirm' | OtherInboundReasonAction;

export function getOtherInboundAvailableActions(
  status: OtherInboundApplication['status'],
  canManage: boolean,
  entryEnabled = true,
): OtherInboundUiAction[] {
  if (!canManage) return [];
  if (status === 'DRAFT') return entryEnabled ? ['edit', 'confirm', 'void'] : ['void'];
  if (status === 'CONFIRMED') return ['cancel'];
  if (status === 'PARTIALLY_RECEIVED') return ['close'];
  return [];
}

export function InboundOrderPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm<OtherInboundApplicationPayload>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingApplication, setEditingApplication] = useState<OtherInboundApplication>();
  const [selectedId, setSelectedId] = useState<number>();
  const [pendingAction, setPendingAction] = useState<OtherInboundReasonAction>();
  const [policyOpen, setPolicyOpen] = useState(false);
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const canManage = hasPermission(session?.currentRole, session?.permissionCodes, 'other_inbound:manage');
  const canConfigurePolicy = session?.currentRole === 'TENANT_ADMIN';
  const policyQuery = useQuery({ queryKey: ['other-inbound-policy'], queryFn: getOtherInboundPolicy });
  const entryEnabled = !policyQuery.isError && policyQuery.data?.enabled === true;
  const detailQuery = useQuery({
    queryKey: ['other-inbound-application', selectedId],
    queryFn: () => getOtherInboundApplication(selectedId as number),
    enabled: selectedId !== undefined && canManage,
  });
  const warehousesQuery = useQuery({
    queryKey: ['other-inbound-reference-warehouses'],
    queryFn: listOtherInboundReferenceWarehouses,
    enabled: formOpen && canManage && entryEnabled,
  });
  const skusQuery = useQuery({
    queryKey: ['other-inbound-reference-skus'],
    queryFn: listOtherInboundReferenceSkus,
    enabled: formOpen && canManage && entryEnabled,
  });
  const createMutation = useMutation({ mutationFn: createOtherInboundApplication });
  const updateMutation = useMutation({
    mutationFn: ({ id, payload, version }: { id: number; payload: OtherInboundApplicationPayload; version: number }) =>
      updateOtherInboundApplication(id, { ...payload, version }),
  });
  const actionMutation = useMutation({
    mutationFn: ({ action, application, reason }: { action: 'confirm' | OtherInboundReasonAction; application: OtherInboundApplication; reason?: string }) =>
      runOtherInboundAction(application.id, action, application.version, reason),
  });
  const policyMutation = useMutation({ mutationFn: updateOtherInboundPolicy });
  const skuMap = useMemo(() => new Map((skusQuery.data ?? []).map((sku) => [sku.id, sku])), [skusQuery.data]);
  const watchedItems = Form.useWatch('items', form) ?? [];
  const selectedApplication = detailQuery.data;
  const detailActions = selectedApplication
    ? getOtherInboundAvailableActions(selectedApplication.status, canManage, entryEnabled)
    : [];

  useEffect(() => {
    if (!formOpen) return;
    form.resetFields();
    if (!editingApplication) {
      form.setFieldsValue({ items: [{ plannedQuantity: 1 }] });
      return;
    }
    form.setFieldsValue({
      businessType: editingApplication.businessType,
      sourceReference: editingApplication.sourceReference,
      businessReason: editingApplication.businessReason,
      warehouseId: editingApplication.warehouseId,
      remark: editingApplication.remark,
      items: editingApplication.items.map((item) => ({
        productSkuId: item.productSkuId,
        plannedQuantity: item.plannedQuantity,
        productionDate: item.productionDate,
        expiryDate: item.expiryDate,
        externalBatchCode: item.externalBatchCode,
        remark: item.remark,
      })),
    });
  }, [editingApplication, form, formOpen]);

  const refresh = async (): Promise<void> => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: OTHER_INBOUND_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: ['other-inbound-application', selectedId] }),
      queryClient.invalidateQueries({ queryKey: ['receiving-tasks'] }),
    ]);
    actionRef.current?.reload();
  };

  const save = async (payload: OtherInboundApplicationPayload): Promise<boolean> => {
    try {
      if (editingApplication) {
        await updateMutation.mutateAsync({ id: editingApplication.id, payload, version: editingApplication.version });
        message.success(t('inbound.messages.updated'));
      } else {
        await createMutation.mutateAsync(payload);
        message.success(t('inbound.messages.created'));
      }
      setFormOpen(false);
      setEditingApplication(undefined);
      await refresh();
      return true;
    } catch (error) { message.error(getErrorMessage(error, t)); return false; }
  };

  const executeAction = async (
    application: OtherInboundApplication,
    action: 'confirm' | OtherInboundReasonAction,
    reason?: string,
  ): Promise<void> => {
    try {
      await actionMutation.mutateAsync({ action, application, reason });
      message.success(t(`inbound.messages.${action}`));
      setPendingAction(undefined);
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const savePolicy = async (payload: OtherInboundPolicyUpdatePayload): Promise<void> => {
    try {
      await policyMutation.mutateAsync(payload);
      message.success(t('inbound.policy.saved'));
      setPolicyOpen(false);
      await queryClient.invalidateQueries({ queryKey: ['other-inbound-policy'] });
      await refresh();
    } catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const columns = useMemo<ProColumns<OtherInboundApplication>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('inbound.searchPlaceholder') } },
    { title: t('inbound.fields.applicationNumber'), dataIndex: 'applicationNumber', width: 180, fixed: 'left', search: false, copyable: true },
    { title: t('inbound.fields.businessType'), dataIndex: 'businessType', width: 170, search: false, render: (_, row) => t(`inbound.businessTypes.${row.businessType}`) },
    { title: t('inbound.fields.sourceReference'), dataIndex: 'sourceReference', width: 180, search: false },
    { title: t('inbound.fields.warehouse'), dataIndex: 'warehouseName', width: 170, search: false },
    { title: t('inbound.fields.status'), dataIndex: 'status', width: 140, search: false, render: (_, row) => <Tag>{t(`inbound.statuses.${row.status}`)}</Tag> },
    { title: t('inbound.fields.plannedQuantity'), dataIndex: 'plannedQuantity', width: 110, align: 'right', search: false },
    { title: t('inbound.fields.receivedQuantity'), dataIndex: 'receivedQuantity', width: 110, align: 'right', search: false },
    { title: t('inbound.fields.createdBy'), dataIndex: 'createdByName', width: 130, search: false },
    { title: t('common.createdAt'), dataIndex: 'createdAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    { title: t('common.actions'), valueType: 'option', fixed: 'right', width: 90, render: (_, row) => [<Button icon={<EyeOutlined />} key="view" onClick={() => setSelectedId(row.id)} size="small" type="link">{t('common.view')}</Button>] },
  ], [i18n.language, t]);

  const itemColumns: TableColumnsType<OtherInboundApplicationItem> = [
    { title: t('inbound.fields.product'), dataIndex: 'productName', width: 220 },
    { title: t('products.fields.barcode'), dataIndex: 'productBarcode', width: 160 },
    { title: t('inbound.fields.plannedQuantity'), dataIndex: 'plannedQuantity', align: 'right', width: 110 },
    { title: t('inbound.fields.receivedQuantity'), dataIndex: 'receivedQuantity', align: 'right', width: 110 },
    { title: t('inbound.fields.closedQuantity'), dataIndex: 'closedQuantity', align: 'right', width: 110 },
    { title: t('purchasing.fields.productionDate'), dataIndex: 'productionDate', width: 140, render: (value) => formatDate(value as string | undefined, i18n.language) },
    { title: t('purchasing.fields.expiryDate'), dataIndex: 'expiryDate', width: 140, render: (value) => formatDate(value as string | undefined, i18n.language) },
    { title: t('purchasing.fields.externalBatchCode'), dataIndex: 'externalBatchCode', width: 170 },
  ];

  if (!canManage) {
    return (
      <>
        <section className="data-page workflow-page">
          <h1>{t('inbound.title')}</h1>
          <Alert
            action={canConfigurePolicy ? <Button icon={<SettingOutlined />} onClick={() => setPolicyOpen(true)}>{t('inbound.policy.configure')}</Button> : undefined}
            description={t('inbound.policy.permissionDenied')}
            message={t('auth.pagePermissionDenied')}
            showIcon
            type="warning"
          />
        </section>
        <OtherInboundPolicyModal
          loading={policyMutation.isPending}
          onCancel={() => setPolicyOpen(false)}
          onConfirm={savePolicy}
          open={policyOpen}
          policy={policyQuery.data}
        />
      </>
    );
  }

  const businessTypeOptions = (policyQuery.data?.allowedBusinessTypes ?? []).map((value) => ({ label: t(`inbound.businessTypes.${value}`), value }));
  const warehouseOptions = (warehousesQuery.data ?? []).filter((warehouse) => warehouse.id !== undefined).map((warehouse) => ({ label: `${warehouse.name ?? '-'} (${warehouse.code ?? '-'})`, value: warehouse.id }));
  const skuOptions = (skusQuery.data ?? []).filter((sku) => sku.id !== undefined).map((sku) => ({ label: `${sku.name ?? '-'} · ${sku.skuCode ?? sku.barcode ?? '-'}`, value: sku.id }));

  return (
    <section className="data-page workflow-page">
      <Alert
        action={canConfigurePolicy ? <Button icon={<SettingOutlined />} onClick={() => setPolicyOpen(true)}>{t('inbound.policy.configure')}</Button> : undefined}
        description={entryEnabled ? undefined : policyQuery.isError ? t('inbound.policy.loadFailed') : t('inbound.policy.disabledDescription')}
        message={entryEnabled ? t('inbound.policy.enabledNotice') : t('inbound.policy.disabledTitle')}
        showIcon
        type={entryEnabled ? 'info' : 'warning'}
      />
      <ProTable<OtherInboundApplication, TableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('inbound.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const applications = await listOtherInboundApplications();
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword ? applications.filter((item) => [item.applicationNumber, item.sourceReference, item.businessReason].some((value) => value?.toLowerCase().includes(keyword))) : applications;
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey="id"
        scroll={{ x: 1540 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          ...(canConfigurePolicy ? [<Button icon={<SettingOutlined />} key="policy" onClick={() => setPolicyOpen(true)}>{t('inbound.policy.configure')}</Button>] : []),
          ...(entryEnabled ? [<Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingApplication(undefined); setFormOpen(true); }} type="primary">{t('inbound.create')}</Button>] : []),
        ]}
      />

      <Drawer
        destroyOnHidden
        extra={selectedApplication ? (
          <Space wrap>
            {detailActions.includes('edit') && <Button icon={<EditOutlined />} onClick={() => { setEditingApplication(selectedApplication); setFormOpen(true); }}>{t('common.edit')}</Button>}
            {detailActions.includes('confirm') && <Popconfirm onConfirm={() => void executeAction(selectedApplication, 'confirm')} title={t('inbound.confirm.notice')}><Button icon={<CheckOutlined />} loading={actionMutation.isPending} type="primary">{t('inbound.actions.confirm')}</Button></Popconfirm>}
            {detailActions.includes('void') && <Button danger icon={<StopOutlined />} onClick={() => setPendingAction('void')}>{t('inbound.actions.void')}</Button>}
            {detailActions.includes('cancel') && <Button danger onClick={() => setPendingAction('cancel')}>{t('inbound.actions.cancel')}</Button>}
            {detailActions.includes('close') && <Button danger onClick={() => setPendingAction('close')}>{t('inbound.actions.close')}</Button>}
          </Space>
        ) : null}
        loading={detailQuery.isLoading}
        onClose={() => setSelectedId(undefined)}
        open={selectedId !== undefined}
        title={t('inbound.details.title', { number: selectedApplication?.applicationNumber ?? '-' })}
        width="min(1080px, 96vw)"
      >
        {detailQuery.error && <Alert description={getErrorMessage(detailQuery.error, t)} message={t('inbound.messages.loadFailed')} showIcon type="error" />}
        {selectedApplication && <>
          <Descriptions
            column={{ xs: 1, sm: 2, lg: 3 }}
            items={[
              { key: 'status', label: t('inbound.fields.status'), children: <Tag>{t(`inbound.statuses.${selectedApplication.status}`)}</Tag> },
              { key: 'type', label: t('inbound.fields.businessType'), children: t(`inbound.businessTypes.${selectedApplication.businessType}`) },
              { key: 'source', label: t('inbound.fields.sourceReference'), children: selectedApplication.sourceReference },
              { key: 'warehouse', label: t('inbound.fields.warehouse'), children: selectedApplication.warehouseName ?? '-' },
              { key: 'planned', label: t('inbound.fields.plannedQuantity'), children: selectedApplication.plannedQuantity ?? 0 },
              { key: 'received', label: t('inbound.fields.receivedQuantity'), children: selectedApplication.receivedQuantity ?? 0 },
              { key: 'reason', label: t('inbound.fields.businessReason'), children: selectedApplication.businessReason, span: 3 },
              { key: 'termination', label: t('inbound.fields.terminationReason'), children: selectedApplication.terminationReason ?? '-', span: 3 },
              { key: 'remark', label: t('common.remark'), children: selectedApplication.remark ?? '-', span: 3 },
            ]}
            size="small"
          />
          <Table columns={itemColumns} dataSource={selectedApplication.items} pagination={false} rowKey="id" scroll={{ x: 1160 }} size="small" />
        </>}
      </Drawer>

      <DrawerForm<OtherInboundApplicationPayload>
        drawerProps={{ destroyOnHidden: true, maskClosable: false }}
        form={form}
        onFinish={save}
        onOpenChange={(open) => { if (!open) { setFormOpen(false); setEditingApplication(undefined); } }}
        open={formOpen && entryEnabled}
        submitter={{ searchConfig: { resetText: t('common.cancel'), submitText: editingApplication ? t('common.save') : t('common.create') }, submitButtonProps: { loading: createMutation.isPending || updateMutation.isPending } }}
        title={editingApplication ? t('inbound.form.editTitle') : t('inbound.form.createTitle')}
        width={980}
      >
        <ProFormSelect label={t('inbound.fields.businessType')} name="businessType" options={businessTypeOptions} rules={[{ required: true, message: t('inbound.validation.businessTypeRequired') }]} width="md" />
        <ProFormText fieldProps={{ maxLength: 120 }} label={t('inbound.fields.sourceReference')} name="sourceReference" rules={[{ required: true, message: t('inbound.validation.sourceReferenceRequired') }]} width="md" />
        <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('inbound.fields.businessReason')} name="businessReason" rules={[{ required: true, message: t('inbound.validation.businessReasonRequired') }]} />
        <ProFormSelect fieldProps={{ loading: warehousesQuery.isLoading, optionFilterProp: 'label', showSearch: true }} label={t('inbound.fields.warehouse')} name="warehouseId" options={warehouseOptions} rules={[{ required: true, message: t('inbound.validation.warehouseRequired') }]} width="md" />
        <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('common.remark')} name="remark" />
        <Form.List name="items" rules={[{ validator: async (_, items) => { if (!items?.length) throw new Error(t('inbound.validation.itemRequired')); } }]}>
          {(fields, { add, remove }) => (
            <div className="other-inbound-lines">
              <strong>{t('inbound.form.items')}</strong>
              {fields.map((field) => {
                const productSkuId = watchedItems[field.name]?.productSkuId;
                const expiryManaged = skuMap.get(productSkuId)?.expiryManagementEnabled === true;
                return (
                  <div className="other-inbound-line" key={field.key}>
                    <Form.Item label={t('inbound.fields.product')} name={[field.name, 'productSkuId']} rules={[{ required: true, message: t('inbound.validation.productRequired') }]}><Select loading={skusQuery.isLoading} optionFilterProp="label" options={skuOptions} showSearch /></Form.Item>
                    <Form.Item label={t('inbound.fields.plannedQuantity')} name={[field.name, 'plannedQuantity']} rules={[{ required: true, message: t('inbound.validation.quantityRequired') }]}><InputNumber min={1} precision={0} /></Form.Item>
                    <Form.Item label={t('purchasing.fields.productionDate')} name={[field.name, 'productionDate']} rules={[{ required: expiryManaged, message: t('purchasing.validation.productionRequired') }]}><Input type="date" /></Form.Item>
                    <Form.Item label={t('purchasing.fields.expiryDate')} name={[field.name, 'expiryDate']} rules={[{ required: expiryManaged, message: t('purchasing.validation.expiryRequired') }]}><Input type="date" /></Form.Item>
                    <Form.Item label={t('purchasing.fields.externalBatchCode')} name={[field.name, 'externalBatchCode']}><Input maxLength={100} /></Form.Item>
                    <Button danger disabled={fields.length === 1} onClick={() => remove(field.name)}>{t('common.delete')}</Button>
                  </div>
                );
              })}
              <Button icon={<PlusOutlined />} onClick={() => add({ plannedQuantity: 1 })} type="dashed">{t('inbound.form.addItem')}</Button>
            </div>
          )}
        </Form.List>
      </DrawerForm>

      <ReasonModal
        danger
        description={pendingAction ? t(`inbound.actions.${pendingAction}Notice`) : ''}
        loading={actionMutation.isPending}
        onCancel={() => setPendingAction(undefined)}
        onConfirm={(reason) => selectedApplication && pendingAction ? executeAction(selectedApplication, pendingAction, reason) : undefined}
        open={pendingAction !== undefined}
        title={pendingAction ? t(`inbound.actions.${pendingAction}`) : ''}
      />
      <OtherInboundPolicyModal
        loading={policyMutation.isPending}
        onCancel={() => setPolicyOpen(false)}
        onConfirm={savePolicy}
        open={policyOpen}
        policy={policyQuery.data}
      />
    </section>
  );
}
