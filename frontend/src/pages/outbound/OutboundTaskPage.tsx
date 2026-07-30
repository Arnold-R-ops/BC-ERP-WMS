import { CheckSquareOutlined, CheckOutlined, EyeOutlined } from '@ant-design/icons';
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Space, Tabs, Tag } from 'antd';
import type { Key } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../../api/errors';
import {
  batchConfirmOutboundTasks,
  confirmOutboundTask,
  listOutboundTasks,
  type ConfirmPickingPayload,
  type OutboundTask,
  type OutboundTaskStatus,
} from '../../api/outbound';
import { paginateArray } from '../../api/pagination';
import { formatDateTime, getStatusColor } from '../workflowUtils';
import { OutboundConfirmModal } from './OutboundConfirmModal';
import { OutboundTaskDrawer } from './OutboundTaskDrawer';

interface OutboundTableParams {
  current?: number;
  pageSize?: number;
  search?: string;
}

type OutboundTab = 'ALL' | OutboundTaskStatus;

function isConfirmable(task: OutboundTask): boolean {
  return task.status === 'PENDING' || task.status === 'PICKING';
}

export function OutboundTaskPage(): JSX.Element {
  const actionRef = useRef<ActionType>();
  const [status, setStatus] = useState<OutboundTab>('PENDING');
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [detailTask, setDetailTask] = useState<OutboundTask>();
  const [confirmTask, setConfirmTask] = useState<OutboundTask>();
  const { i18n, t } = useTranslation();
  const { message, modal } = AntdApp.useApp();

  useEffect(() => {
    setSelectedRowKeys([]);
    actionRef.current?.reloadAndRest?.();
  }, [status]);

  const singleMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ConfirmPickingPayload }) => confirmOutboundTask(id, payload),
  });
  const batchMutation = useMutation({ mutationFn: batchConfirmOutboundTasks });

  const refresh = (): void => {
    setSelectedRowKeys([]);
    actionRef.current?.reload();
  };

  const submitSingle = async (payload: ConfirmPickingPayload): Promise<void> => {
    if (confirmTask?.id === undefined) return;
    try {
      await singleMutation.mutateAsync({ id: confirmTask.id, payload });
      message.success(t('outbound.messages.confirmed'));
      setConfirmTask(undefined);
      setDetailTask(undefined);
      refresh();
    } catch (error) {
      message.error(getErrorMessage(error, t));
    }
  };

  const submitBatch = (): void => {
    const taskIds = selectedRowKeys
      .map((key) => Number(key))
      .filter((id) => Number.isSafeInteger(id));
    if (taskIds.length === 0) return;

    modal.confirm({
      title: t('outbound.batch.title'),
      content: t('outbound.batch.notice', { count: taskIds.length }),
      okText: t('outbound.actions.batchConfirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await batchMutation.mutateAsync(taskIds);
          message.success(t('outbound.messages.batchConfirmed', { count: taskIds.length }));
          refresh();
        } catch (error) {
          message.error(getErrorMessage(error, t));
          throw error;
        }
      },
    });
  };

  const columns = useMemo<ProColumns<OutboundTask>[]>(() => [
    { title: t('common.search'), dataIndex: 'search', hideInTable: true, fieldProps: { placeholder: t('outbound.searchPlaceholder') } },
    { title: t('outbound.fields.routeSequence'), valueType: 'index', width: 80, align: 'center', search: false },
    { title: t('outbound.fields.salesOrderNo'), dataIndex: 'salesOrderNo', width: 180, fixed: 'left', copyable: true, search: false },
    { title: t('outbound.fields.product'), dataIndex: 'productName', width: 220, search: false },
    { title: t('outbound.fields.planQty'), dataIndex: 'planQty', width: 100, align: 'right', search: false },
    { title: t('outbound.fields.actualQty'), dataIndex: 'actualQty', width: 100, align: 'right', search: false },
    { title: t('outbound.fields.location'), dataIndex: 'locationCode', width: 230, copyable: true, search: false },
    { title: t('outbound.fields.batchCode'), dataIndex: 'batchCode', width: 250, copyable: true, search: false },
    {
      title: t('outbound.fields.status'), dataIndex: 'status', width: 120, search: false,
      render: (_, task) => <Tag color={getStatusColor(task.status)}>{t(`outbound.statuses.${task.status}`, { defaultValue: task.statusDescription ?? task.status })}</Tag>,
    },
    { title: t('outbound.fields.pickedAt'), dataIndex: 'pickedAt', width: 175, search: false, renderText: (value) => formatDateTime(value as string | undefined, i18n.language) },
    {
      title: t('common.actions'), valueType: 'option', width: 180, fixed: 'right',
      render: (_, task) => (
        <Space size={0}>
          <Button icon={<EyeOutlined />} onClick={() => setDetailTask(task)} size="small" type="link">{t('common.view')}</Button>
          {isConfirmable(task) ? (
            <Button icon={<CheckOutlined />} onClick={() => setConfirmTask(task)} size="small" type="link">{t('outbound.actions.confirm')}</Button>
          ) : null}
        </Space>
      ),
    },
  ], [i18n.language, t]);

  const tabs: OutboundTab[] = ['PENDING', 'PICKING', 'COMPLETED', 'ALL'];

  return (
    <section className="data-page workflow-page">
      <Tabs
        activeKey={status}
        items={tabs.map((value) => ({ key: value, label: t(`outbound.tabs.${value}`) }))}
        onChange={(key) => setStatus(key as OutboundTab)}
      />
      <ProTable<OutboundTask, OutboundTableParams>
        actionRef={actionRef}
        columns={columns}
        headerTitle={t('outbound.title')}
        options={{ density: true, fullScreen: true, reload: true, setting: true }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        request={async (params) => {
          try {
            const tasks = await listOutboundTasks({ status: status === 'ALL' ? undefined : status });
            const keyword = params.search?.trim().toLowerCase();
            const filtered = keyword
              ? tasks.filter((task) => [task.salesOrderNo, task.productName, task.productBarcode, task.batchCode, task.locationCode]
                .some((value) => value?.toLowerCase().includes(keyword)))
              : tasks;
            return paginateArray(filtered, params);
          } catch (error) {
            message.error(getErrorMessage(error, t));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
          getCheckboxProps: (task) => ({ disabled: task.id === undefined || !isConfirmable(task) }),
        }}
        scroll={{ x: 1645 }}
        search={{ defaultCollapsed: false, labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button
            disabled={selectedRowKeys.length === 0}
            icon={<CheckSquareOutlined />}
            key="batch-confirm"
            loading={batchMutation.isPending}
            onClick={submitBatch}
            type="primary"
          >
            {t('outbound.actions.batchConfirmSelected', { count: selectedRowKeys.length })}
          </Button>,
        ]}
      />

      <OutboundTaskDrawer
        onClose={() => setDetailTask(undefined)}
        onConfirm={setConfirmTask}
        open={detailTask !== undefined}
        task={detailTask}
      />
      <OutboundConfirmModal
        loading={singleMutation.isPending}
        onCancel={() => setConfirmTask(undefined)}
        onConfirm={submitSingle}
        open={confirmTask !== undefined}
        task={confirmTask}
      />
    </section>
  );
}
