import { CheckOutlined, CloseOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Descriptions, Drawer, Space, Spin, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import type { EmergencyCorrection } from '../../api/emergencyCorrections';
import { getLocation } from '../../api/masterData';
import { getProductSku } from '../../api/productSkus';
import { formatDateTime, getStatusColor } from '../workflowUtils';
import type { CorrectionAction } from './EmergencyCorrectionActionModal';

interface EmergencyCorrectionDrawerProps {
  canAdjust: boolean;
  canApprove: boolean;
  correction?: EmergencyCorrection;
  onAction: (action: CorrectionAction, correction: EmergencyCorrection) => void;
  onApply: (correction: EmergencyCorrection) => void;
  onClose: () => void;
}

export function EmergencyCorrectionDrawer({
  canAdjust,
  canApprove,
  correction,
  onAction,
  onApply,
  onClose,
}: EmergencyCorrectionDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const open = correction !== undefined;
  const productQuery = useQuery({
    queryKey: ['product-sku', correction?.productSkuId],
    queryFn: () => getProductSku(correction?.productSkuId as number),
    enabled: open && correction?.productSkuId !== undefined,
  });
  const locationQuery = useQuery({
    queryKey: ['location', correction?.locationId],
    queryFn: () => getLocation(correction?.locationId as number),
    enabled: open && correction?.locationId !== undefined,
  });
  const adjustment = correction?.adjustmentQty ?? 0;

  const actions = (
    <Space wrap>
      {correction?.status === 'DRAFT' && canAdjust ? (
        <Button icon={<SendOutlined />} onClick={() => onAction('submit', correction)} type="primary">{t('corrections.actions.submit')}</Button>
      ) : null}
      {correction?.status === 'PENDING_REVIEW' && canApprove ? (
        <>
          <Button icon={<CheckOutlined />} onClick={() => onAction('review', correction)} type="primary">{t('corrections.actions.review')}</Button>
          <Button danger icon={<CloseOutlined />} onClick={() => onAction('reject', correction)}>{t('corrections.actions.reject')}</Button>
        </>
      ) : null}
      {correction?.status === 'PENDING_APPROVAL' && canApprove ? (
        <>
          <Button danger icon={<CheckOutlined />} onClick={() => onAction('approve', correction)} type="primary">{t('corrections.actions.approve')}</Button>
          <Button danger icon={<CloseOutlined />} onClick={() => onAction('reject', correction)}>{t('corrections.actions.reject')}</Button>
        </>
      ) : null}
      {correction?.status === 'APPROVED' && canAdjust ? (
        <Button danger icon={<ThunderboltOutlined />} onClick={() => onApply(correction)} type="primary">{t('corrections.actions.apply')}</Button>
      ) : null}
    </Space>
  );

  return (
    <Drawer
      extra={actions}
      onClose={onClose}
      open={open}
      title={t('corrections.details.title', { correctionNo: correction?.correctionNo ?? '-' })}
      width={820}
    >
      <Spin spinning={productQuery.isLoading || locationQuery.isLoading}>
        <Descriptions
          bordered
          column={2}
          items={[
            { key: 'status', label: t('corrections.fields.status'), children: <Tag color={getStatusColor(correction?.status)}>{correction?.status ? t(`corrections.statuses.${correction.status}`) : '-'}</Tag> },
            { key: 'number', label: t('corrections.fields.correctionNo'), children: correction?.correctionNo ?? '-' },
            { key: 'product', label: t('corrections.fields.product'), children: productQuery.data ? `${productQuery.data.skuCode ?? '-'} · ${productQuery.data.name ?? productQuery.data.skuName ?? '-'}` : correction?.productSkuId ?? '-' },
            { key: 'location', label: t('corrections.fields.location'), children: locationQuery.data?.locationCode ?? correction?.locationId ?? '-' },
            { key: 'batch', label: t('corrections.fields.batchCode'), children: correction?.batchCode ?? '-' },
            { key: 'expiry', label: t('corrections.fields.expiryDate'), children: correction?.expiryDate ?? '-' },
            { key: 'system', label: t('corrections.fields.systemQty'), children: correction?.systemQty ?? 0 },
            { key: 'counted', label: t('corrections.fields.countedQty'), children: correction?.countedQty ?? 0 },
            { key: 'adjustment', label: t('corrections.fields.adjustmentQty'), children: <Tag color={adjustment === 0 ? 'default' : adjustment > 0 ? 'success' : 'error'}>{adjustment > 0 ? `+${adjustment}` : adjustment}</Tag> },
            { key: 'order', label: t('corrections.fields.relatedSalesOrderId'), children: correction?.relatedSalesOrderId ?? '-' },
            { key: 'reason', label: t('corrections.fields.reasonCode'), children: correction?.reasonCode ? t(`corrections.reasons.${correction.reasonCode}`, { defaultValue: correction.reasonCode }) : '-' },
            { key: 'detail', label: t('corrections.fields.reasonDetail'), children: correction?.reasonDetail ?? '-', span: 2 },
            { key: 'evidence', label: t('corrections.fields.evidenceUrl'), children: correction?.evidenceUrl ? <a href={correction.evidenceUrl} rel="noreferrer" target="_blank">{correction.evidenceUrl}</a> : '-', span: 2 },
            { key: 'reviewComment', label: t('corrections.fields.reviewComment'), children: correction?.reviewComment ?? '-', span: 2 },
            { key: 'approvalComment', label: t('corrections.fields.approvalComment'), children: correction?.approvalComment ?? '-', span: 2 },
            { key: 'created', label: t('common.createdAt'), children: formatDateTime(correction?.createdAt, i18n.language) },
            { key: 'updated', label: t('common.updatedAt'), children: formatDateTime(correction?.updatedAt, i18n.language) },
          ]}
          size="small"
        />
      </Spin>
    </Drawer>
  );
}
