import { Alert, Button, Descriptions, Modal, Space, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import type { EmergencyCorrection, EmergencyCorrectionImpact } from '../../api/emergencyCorrections';
import { formatDateTime } from '../workflowUtils';
import { getCorrectionPreviewState } from './correctionPreview';

interface EmergencyCorrectionApplyModalProps {
  correction?: EmergencyCorrection;
  hasPriorAttempt: boolean;
  impact?: EmergencyCorrectionImpact;
  loading: boolean;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
  onStartNewAttempt: () => Promise<void>;
}

function signedQuantity(value: number): string {
  return value > 0 ? `+${value}` : String(value);
}

export function EmergencyCorrectionApplyModal({
  correction,
  hasPriorAttempt,
  impact,
  loading,
  onCancel,
  onConfirm,
  onStartNewAttempt,
}: EmergencyCorrectionApplyModalProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const affectedOrders = impact?.affectedSalesOrderIds ?? [];
  const previewState = getCorrectionPreviewState(impact);

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okButtonProps={{
        danger: true,
        disabled: !impact?.canApply || previewState !== 'READY',
      }}
      okText={t('corrections.apply.confirm')}
      onCancel={onCancel}
      onOk={onConfirm}
      open={correction !== undefined && impact !== undefined}
      title={t('corrections.apply.title', { correctionNo: correction?.correctionNo ?? '-' })}
      width={720}
    >
      <Space direction="vertical" size="middle" style={{ display: 'flex' }}>
        <Alert
          description={t('corrections.apply.deltaNotice')}
          message={t('corrections.apply.previewTitle')}
          showIcon
          type="warning"
        />
        <Descriptions
          bordered
          column={2}
          items={[
            { key: 'snapshot', label: t('corrections.apply.snapshot'), children: impact?.snapshotSystemQty ?? '-' },
            { key: 'counted', label: t('corrections.apply.counted'), children: impact?.countedQty ?? '-' },
            {
              key: 'delta',
              label: t('corrections.apply.delta'),
              children: impact ? (
                <Tag color={impact.adjustmentQty === 0 ? 'default' : impact.adjustmentQty > 0 ? 'success' : 'error'}>
                  {signedQuantity(impact.adjustmentQty)}
                </Tag>
              ) : '-',
            },
            { key: 'current', label: t('corrections.apply.current'), children: impact?.currentQty ?? '-' },
            { key: 'reserved', label: t('corrections.apply.reserved'), children: impact?.reservedQty ?? '-' },
            { key: 'projected', label: t('corrections.apply.projected'), children: impact?.projectedQty ?? '-' },
            { key: 'previewExpiresAt', label: t('corrections.apply.previewExpiresAt'), children: formatDateTime(impact?.previewExpiresAt, i18n.language), span: 2 },
          ]}
          size="small"
        />
        {impact?.adjustmentQty === 0 ? (
          <Alert message={t('corrections.apply.zeroDelta')} showIcon type="info" />
        ) : null}
        {!impact?.canApply ? (
          <Alert
            description={t('corrections.apply.blockedDescription')}
            message={t('corrections.apply.blockedTitle')}
            showIcon
            type="error"
          />
        ) : null}
        {previewState !== 'READY' ? (
          <Alert
            description={t('corrections.apply.previewUnavailableDescription')}
            message={t(`corrections.apply.previewStates.${previewState}`)}
            showIcon
            type="error"
          />
        ) : null}
        {hasPriorAttempt ? (
          <Alert
            action={<Button onClick={onStartNewAttempt} size="small">{t('corrections.apply.startNewAttempt')}</Button>}
            description={t('corrections.apply.priorAttemptDescription')}
            message={t('corrections.apply.priorAttemptTitle')}
            showIcon
            type="warning"
          />
        ) : null}
        {affectedOrders.length > 0 ? (
          <div>
            <Typography.Text strong>{t('corrections.apply.affectedOrders')}</Typography.Text>
            <Space size={[4, 8]} style={{ display: 'flex', marginTop: 8 }} wrap>
              {affectedOrders.map((orderId) => <Tag color="warning" key={orderId}>#{orderId}</Tag>)}
            </Space>
          </div>
        ) : (
          <Typography.Text type="secondary">{t('corrections.apply.noAffectedOrders')}</Typography.Text>
        )}
      </Space>
    </Modal>
  );
}
