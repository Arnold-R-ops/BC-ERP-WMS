import { CheckOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Drawer, Progress, Space, Spin, Tag } from 'antd';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getStocktakeTask,
  listStocktakeItems,
  listStocktakeReviewItems,
  type StocktakeItem,
  type StocktakeReviewItem,
  type StocktakeTask,
} from '../../api/stocktake';
import { formatDateTime, getStatusColor } from '../workflowUtils';

interface StocktakeTaskDrawerProps {
  canCount: boolean;
  canReview: boolean;
  onClose: () => void;
  onCount: (item: StocktakeItem) => void;
  onReview: (task: StocktakeTask) => void;
  onStart: (task: StocktakeTask) => void;
  open: boolean;
  taskId?: number;
}

export function StocktakeTaskDrawer({
  canCount,
  canReview,
  onClose,
  onCount,
  onReview,
  onStart,
  open,
  taskId,
}: StocktakeTaskDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const taskQuery = useQuery({
    queryKey: ['stocktake-task', taskId],
    queryFn: () => getStocktakeTask(taskId as number),
    enabled: open && taskId !== undefined,
  });
  const task = taskQuery.data;
  const reviewData = canReview && (task?.status === 'REVIEWING' || task?.status === 'COMPLETED');
  const itemAccess = reviewData || (canCount && (task?.status === 'CREATED' || task?.status === 'COUNTING'));
  const itemsQuery = useQuery({
    queryKey: ['stocktake-items', taskId, reviewData ? 'review' : 'blind'],
    queryFn: () => reviewData
      ? listStocktakeReviewItems(taskId as number)
      : listStocktakeItems(taskId as number),
    enabled: open && taskId !== undefined && itemAccess,
  });

  const columns = useMemo<ProColumns<StocktakeItem | StocktakeReviewItem>[]>(() => {
    const result: ProColumns<StocktakeItem | StocktakeReviewItem>[] = [
      { title: t('stocktake.fields.product'), dataIndex: 'productName', width: 210 },
      { title: t('stocktake.fields.batchCode'), dataIndex: 'batchCode', width: 220, copyable: true },
      { title: t('stocktake.fields.location'), dataIndex: 'locationCode', width: 210, copyable: true },
    ];
    if (reviewData) {
      result.push(
        { title: t('stocktake.fields.snapshotQty'), dataIndex: 'snapshotQty', width: 105, align: 'right' },
        { title: t('stocktake.fields.countedQty'), dataIndex: 'countedQty', width: 105, align: 'right' },
        {
          title: t('stocktake.fields.differenceQty'), dataIndex: 'differenceQty', width: 105, align: 'right',
          render: (_, item) => {
            const difference = (item as StocktakeReviewItem).differenceQty ?? 0;
            return <strong className={difference === 0 ? 'quantity-ok' : 'quantity-danger'}>{difference > 0 ? `+${difference}` : difference}</strong>;
          },
        },
      );
    } else {
      result.push(
        {
          title: t('stocktake.fields.countStatus'), dataIndex: 'isCounted', width: 105,
          render: (_, item) => <Tag color={item.isCounted ? 'success' : 'default'}>{t(`stocktake.countStatus.${item.isCounted ? 'COUNTED' : 'PENDING'}`)}</Tag>,
        },
        { title: t('stocktake.fields.countedQty'), dataIndex: 'countedQty', width: 105, align: 'right', renderText: (value) => value ?? '-' },
      );
    }
    result.push({
      title: t('common.actions'), valueType: 'option', width: 105, fixed: 'right',
      render: (_, item) => task?.status === 'COUNTING' && canCount ? [
        <Button key="count" onClick={() => onCount(item)} size="small" type="link">
          {item.isCounted ? t('stocktake.actions.recount') : t('stocktake.actions.count')}
        </Button>,
      ] : [],
    });
    return result;
  }, [canCount, onCount, reviewData, t, task?.status]);

  const extra = (
    <Space>
      {task?.status === 'CREATED' && canCount ? (
        <Button icon={<PlayCircleOutlined />} onClick={() => onStart(task)} type="primary">
          {t('stocktake.actions.start')}
        </Button>
      ) : null}
      {task?.status === 'REVIEWING' && canReview ? (
        <Button icon={<CheckOutlined />} onClick={() => onReview(task)} type="primary">
          {t('stocktake.actions.review')}
        </Button>
      ) : null}
    </Space>
  );

  return (
    <Drawer
      extra={extra}
      onClose={onClose}
      open={open}
      title={t('stocktake.details.title', { taskNo: task?.taskNo ?? '-' })}
      width={1080}
    >
      <Spin spinning={taskQuery.isLoading}>
        <Descriptions
          bordered
          column={3}
          items={[
            { key: 'status', label: t('stocktake.fields.status'), children: <Tag color={getStatusColor(task?.status)}>{task?.statusDescription ?? task?.status ?? '-'}</Tag> },
            { key: 'warehouse', label: t('stocktake.fields.warehouse'), children: task?.warehouseName ?? '-' },
            { key: 'cycle', label: t('stocktake.fields.cycleType'), children: task?.cycleTypeDescription ?? task?.cycleType ?? '-' },
            { key: 'snapshot', label: t('stocktake.fields.snapshotTime'), children: formatDateTime(task?.snapshotTime, i18n.language) },
            { key: 'creator', label: t('stocktake.fields.createdBy'), children: task?.createdByName ?? task?.createdBy ?? '-' },
            { key: 'reviewer', label: t('stocktake.fields.reviewedBy'), children: task?.reviewedByName ?? task?.reviewedBy ?? '-' },
            { key: 'progress', label: t('stocktake.fields.progress'), children: <Progress percent={task?.progress ?? 0} size="small" />, span: 2 },
            { key: 'differences', label: t('stocktake.fields.differenceItems'), children: task?.differenceItems ?? 0 },
            { key: 'comment', label: t('workflow.comment'), children: task?.reviewComment ?? '-', span: 3 },
          ]}
          size="small"
        />

        {!itemAccess && task ? (
          <Alert message={t('stocktake.details.itemPermissionNotice')} showIcon style={{ marginTop: 16 }} type="info" />
        ) : null}
        {!reviewData && itemAccess ? (
          <Alert message={t('stocktake.count.blindNotice')} showIcon style={{ marginTop: 16 }} type="info" />
        ) : null}

        {itemAccess ? (
          <ProTable<StocktakeItem | StocktakeReviewItem>
            columns={columns}
            dataSource={itemsQuery.data ?? []}
            loading={itemsQuery.isLoading}
            options={false}
            pagination={{ defaultPageSize: 20, showSizeChanger: true }}
            rowKey="id"
            search={false}
            scroll={{ x: reviewData ? 1060 : 850 }}
            style={{ marginTop: 16 }}
          />
        ) : null}
      </Spin>
    </Drawer>
  );
}
