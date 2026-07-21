import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Button, Switch, Tag, Tooltip } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  createCustomer,
  listCustomers,
  updateCustomer,
  type Customer,
  type CustomerPayload,
} from '../../api/masterData';
import { paginateArray } from '../../api/pagination';
import { formatDateTime, formatMoney } from '../workflowUtils';
import { ClientFormModal } from './ClientFormModal';

interface ClientTableParams { current?: number; pageSize?: number; search?: string }

function customerPayload(customer: Customer, isActive = customer.isActive): CustomerPayload {
  return {
    code: customer.code ?? '',
    name: customer.name ?? '',
    contact: customer.contact,
    phone: customer.phone,
    email: customer.email,
    address: customer.address,
    creditLimit: customer.creditLimit,
    isActive,
  };
}

export function ClientPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<Customer>();
  const { i18n, t } = useTranslation();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id?: number; payload: CustomerPayload }) =>
      id === undefined ? createCustomer(payload) : updateCustomer(id, payload),
  });
  const statusMutation = useMutation({
    mutationFn: ({ customer, active }: { customer: Customer; active: boolean }) =>
      updateCustomer(customer.id as number, customerPayload(customer, active)),
  });

  const refresh = async (): Promise<void> => {
    await queryClient.invalidateQueries({ queryKey: ['customers'] });
    actionRef.current?.reload();
  };

  const save = async (payload: CustomerPayload): Promise<boolean> => {
    try {
      await saveMutation.mutateAsync({ id: editingCustomer?.id, payload });
      message.success(t(editingCustomer ? 'clients.messages.updated' : 'clients.messages.created'));
      setFormOpen(false);
      setEditingCustomer(undefined);
      await refresh();
      return true;
    } catch (error) {
      message.error(getErrorMessage(error, t));
      return false;
    }
  };

  const toggleActive = async (customer: Customer, active: boolean): Promise<void> => {
    if (customer.id === undefined) return;
    try {
      await statusMutation.mutateAsync({ customer, active });
      message.success(t(active ? 'clients.messages.activated' : 'clients.messages.deactivated'));
      await refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const columns = useMemo<ProColumns<Customer>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('clients.searchPlaceholder') } },
    { title: t('clients.fields.code'), dataIndex: 'code', width: 150, fixed: 'left', search: false, copyable: true },
    { title: t('clients.fields.name'), dataIndex: 'name', width: 210, search: false },
    { title: t('clients.fields.contact'), dataIndex: 'contact', width: 140, search: false },
    { title: t('clients.fields.phone'), dataIndex: 'phone', width: 150, search: false },
    { title: t('clients.fields.email'), dataIndex: 'email', width: 210, search: false, ellipsis: true },
    { title: t('clients.fields.creditLimit'), dataIndex: 'creditLimit', width: 150, align: 'right', search: false, renderText: (value) => formatMoney(value as number | undefined, i18n.language) },
    { title: t('clients.fields.type'), dataIndex: 'customerType', width: 120, search: false, render: () => <Tag color="blue">{t('clients.type')}</Tag> },
    {
      title: t('clients.fields.active'), dataIndex: 'isActive', width: 110, search: false,
      render: (_, row) => (
        <Switch
          checked={row.isActive === true}
          checkedChildren={t('common.enabled')}
          loading={statusMutation.isPending}
          onChange={(checked) => void toggleActive(row, checked)}
          unCheckedChildren={t('common.disabled')}
        />
      ),
    },
    { title: t('common.updatedAt'), dataIndex: 'updatedAt', width: 180, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: 90, fixed: 'right',
      render: (_, row) => [
        <Tooltip key="edit" title={t('common.edit')}>
          <Button aria-label={t('common.edit')} icon={<EditOutlined />} onClick={() => { setEditingCustomer(row); setFormOpen(true); }} size="small" type="text" />
        </Tooltip>,
      ],
    },
  ], [i18n.language, statusMutation.isPending, t]);

  return (
    <section className="data-page">
      <ProTable<Customer, ClientTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('clients.title')}
        options={{ density: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const customers = await listCustomers(false, 'CLIENT');
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword
              ? customers.filter((customer) => [customer.code, customer.name, customer.contact, customer.email]
                .some((value) => value?.toLowerCase().includes(keyword)))
              : customers;
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1580 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button icon={<PlusOutlined />} key="create" onClick={() => { setEditingCustomer(undefined); setFormOpen(true); }} type="primary">
            {t('clients.actions.create')}
          </Button>,
        ]}
      />

      <ClientFormModal
        customer={editingCustomer}
        loading={saveMutation.isPending}
        onClose={() => { setFormOpen(false); setEditingCustomer(undefined); }}
        onSubmit={save}
        open={formOpen}
      />
    </section>
  );
}
