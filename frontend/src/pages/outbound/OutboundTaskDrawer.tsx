import { CheckOutlined } from '@ant-design/icons';
import { Button, Descriptions, Drawer, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import type { OutboundTask } from '../../api/outbound';
import { formatDateTime, getStatusColor } from '../workflowUtils';

interface OutboundTaskDrawerProps {
  onClose: () => void;
  onConfirm: (task: OutboundTask) => void;
  open: boolean;
  task?: OutboundTask;
}

export function OutboundTaskDrawer({
  onClose,
  onConfirm,
  open,
  task,
}: OutboundTaskDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const canConfirm = task?.status === 'PENDING' || task?.status === 'PICKING';

  return (
    <Drawer
      extra={canConfirm ? (
        <Button icon={<CheckOutlined />} onClick={() => task && onConfirm(task)} type="primary">
          {t('outbound.actions.confirm')}
        </Button>
      ) : null}
      onClose={onClose}
      open={open}
      title={t('outbound.details.title', { id: task?.id ?? '-' })}
      width={680}
    >
      <Descriptions
        bordered
        column={2}
        items={[
          { key: 'status', label: t('outbound.fields.status'), children: <Tag color={getStatusColor(task?.status)}>{task?.statusDescription ?? task?.status ?? '-'}</Tag> },
          { key: 'order', label: t('outbound.fields.salesOrderNo'), children: task?.salesOrderNo ?? '-' },
          { key: 'product', label: t('outbound.fields.product'), children: task?.productName ?? '-' },
          { key: 'barcode', label: t('outbound.fields.productBarcode'), children: task?.productBarcode ?? '-' },
          { key: 'batch', label: t('outbound.fields.batchCode'), children: task?.batchCode ?? '-' },
          { key: 'location', label: t('outbound.fields.location'), children: task?.locationCode ?? '-' },
          { key: 'planned', label: t('outbound.fields.planQty'), children: task?.planQty ?? 0 },
          { key: 'actual', label: t('outbound.fields.actualQty'), children: task?.actualQty ?? 0 },
          { key: 'picker', label: t('outbound.fields.pickedBy'), children: task?.pickedByName ?? task?.pickedBy ?? '-' },
          { key: 'pickedAt', label: t('outbound.fields.pickedAt'), children: formatDateTime(task?.pickedAt, i18n.language) },
          { key: 'created', label: t('common.createdAt'), children: formatDateTime(task?.createdAt, i18n.language), span: 2 },
          { key: 'remark', label: t('common.remark'), children: task?.remark ?? '-', span: 2 },
        ]}
        size="small"
      />
    </Drawer>
  );
}
