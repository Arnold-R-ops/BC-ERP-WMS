import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Popconfirm, Switch, Tooltip } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { hasPermission } from '../../access';
import { useAuth } from '../../auth/AuthProvider';
import { getErrorMessage } from '../../api/errors';
import { paginateArray } from '../../api/pagination';
import {
  createSupplier,
  deleteSupplier,
  listSuppliers,
  setSupplierActive,
  SUPPLIERS_QUERY_KEY,
  updateSupplier,
  type Supplier,
} from '../../api/suppliers';
import { formatDateTime } from '../workflowUtils';
import { SupplierFormModal, type SupplierFormValues } from './SupplierFormModal';

interface SupplierTableParams { current?: number; pageSize?: number; search?: string }

export function SupplierPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingSupplier, setEditingSupplier] = useState<Supplier>();
  const { session } = useAuth();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const canCreate = hasPermission(session?.currentRole, session?.permissionCodes, 'supplier:create');
  const canEdit = hasPermission(session?.currentRole, session?.permissionCodes, 'supplier:update');
  const canDelete = hasPermission(session?.currentRole, session?.permissionCodes, 'supplier:delete');

  const saveMutation = useMutation({
    mutationFn: ({ id, values }: { id?: number; values: SupplierFormValues }) => {
      if (id === undefined) return createSupplier(values);
      const { code: _code, ...payload } = values;
      return updateSupplier(id, payload);
    },
  });
  const activeMutation = useMutation({ mutationFn: ({ id, active }: { id: number; active: boolean }) => setSupplierActive(id, active) });
  const deleteMutation = useMutation({ mutationFn: deleteSupplier });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: SUPPLIERS_QUERY_KEY });
    actionRef.current?.reload();
  };

  const save = async (values: SupplierFormValues): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingSupplier?.id, values });
      message.success(t(editingSupplier ? 'suppliers.messages.updated' : 'suppliers.messages.created'));
      setFormOpen(false);
      setEditingSupplier(undefined);
      await refresh();
      return true;
    } catch (error) { message.error(getErrorMessage(error, t)); return false; }
  };

  const toggleActive = async (supplier: Supplier, active: boolean): Promise<void> => {
    if (supplier.id === undefined) return;
    try { await activeMutation.mutateAsync({ id: supplier.id, active }); message.success(t(active ? 'suppliers.messages.activated' : 'suppliers.messages.deactivated')); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const remove = async (supplier: Supplier): Promise<void> => {
    if (supplier.id === undefined) return;
    try { await deleteMutation.mutateAsync(supplier.id); message.success(t('suppliers.messages.deleted')); await refresh(); }
    catch (error) { message.error(getErrorMessage(error, t)); }
  };

  const columns = useMemo<ProColumns<Supplier>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('suppliers.searchPlaceholder') } },
    { title: t('suppliers.fields.code'), dataIndex: 'code', width: 150, fixed: 'left', search: false, copyable: true },
    { title: t('suppliers.fields.name'), dataIndex: 'name', width: 210, search: false },
    { title: t('suppliers.fields.contact'), dataIndex: 'contact', width: 140, search: false },
    { title: t('suppliers.fields.phone'), dataIndex: 'phone', width: 150, search: false },
    { title: t('suppliers.fields.email'), dataIndex: 'email', width: 210, search: false, ellipsis: true },
    { title: t('suppliers.fields.address'), dataIndex: 'address', width: 260, search: false, ellipsis: true },
    {
      title: t('suppliers.fields.active'), dataIndex: 'isActive', width: 110, search: false,
      render: (_, row) => <Switch checked={row.isActive === true} checkedChildren={t('common.enabled')} disabled={!canEdit} loading={activeMutation.isPending} onChange={(checked) => void toggleActive(row, checked)} unCheckedChildren={t('common.disabled')} />,
    },
    { title: t('common.updatedAt'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: canEdit || canDelete ? 110 : 0, fixed: 'right', hideInTable: !canEdit && !canDelete,
      render: (_, row) => [
        canEdit ? <Tooltip key="edit" title={t('common.edit')}><Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditingSupplier(row); setFormOpen(true); }} size="small" type="text" /></Tooltip> : null,
        canDelete ? <Popconfirm disabled={row.isActive === true} key="delete" onConfirm={() => void remove(row)} title={t('suppliers.confirmDelete')}>
          <Tooltip title={row.isActive ? t('suppliers.deactivateBeforeDelete') : t('common.delete')}><Button aria-label={t('common.delete')} danger disabled={row.isActive === true} icon={<DeleteOutlined />} loading={deleteMutation.isPending} size="small" type="text" /></Tooltip>
        </Popconfirm> : null,
      ].filter(Boolean),
    },
  ], [activeMutation.isPending, canDelete, canEdit, deleteMutation.isPending, i18n.language, t]);

  return (
    <section className="data-page">
      <ProTable<Supplier, SupplierTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('suppliers.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const suppliers = await listSuppliers(false);
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword ? suppliers.filter((supplier) => [supplier.code, supplier.name, supplier.contact, supplier.email].some((value) => value?.toLowerCase().includes(keyword))) : suppliers;
            return paginateArray(filtered, params);
          } catch (error) { message.error(getErrorMessage(error, t)); return { data: [], success: false, total: 0 }; }
        }}
        rowKey="id"
        scroll={{ x: 1580 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => canCreate ? [<Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingSupplier(undefined); setFormOpen(true); }} type="primary">{t('suppliers.actions.create')}</Button>] : []}
      />
      <SupplierFormModal loading={saveMutation.isPending} onClose={() => { setFormOpen(false); setEditingSupplier(undefined); }} onSubmit={save} open={formOpen} supplier={editingSupplier} />
    </section>
  );
}
