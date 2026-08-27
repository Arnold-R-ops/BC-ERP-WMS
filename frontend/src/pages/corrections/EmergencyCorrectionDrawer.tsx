import { CheckOutlined, CloseOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Button, Descriptions, Drawer, Space, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import type { EmergencyCorrection, EmergencyCorrectionApprovalMode } from '../../api/emergencyCorrections';
import { formatDateTime, getStatusColor } from '../workflowUtils';
import type { CorrectionAction } from './EmergencyCorrectionActionModal';

function safeHttpUrl(value: string): string | undefined {
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : undefined;
  } catch {
    return undefined;
  }
}

interface EmergencyCorrectionDrawerProps {
  canAdjust: boolean;
  canApprove: boolean;
  canReject: boolean;
  canReview: boolean;
  correction?: EmergencyCorrection;
  effectiveApprovalMode?: EmergencyCorrectionApprovalMode;
  onAction: (action: CorrectionAction, correction: EmergencyCorrection) => void;
  onApply: (correction: EmergencyCorrection) => void;
  onClose: () => void;
  policyLoading: boolean;
  previewLoading: boolean;
}

export function EmergencyCorrectionDrawer({
  canAdjust,
  canApprove,
  canReject,
  canReview,
  correction,
  effectiveApprovalMode,
  onAction,
  onApply,
  onClose,
  policyLoading,
  previewLoading,
}: EmergencyCorrectionDrawerProps): JSX.Element {
  const { i18n, t } = useTranslation();
  const open = correction !== undefined;
  const adjustment = correction?.adjustmentQty ?? 0;
  const draftApprovalMode = correction?.approvalModeSnapshot ?? effectiveApprovalMode;
  const evidenceUrl = correction?.evidenceUrl;
  const safeEvidenceUrl = evidenceUrl ? safeHttpUrl(evidenceUrl) : undefined;
  const productLabel = correction?.productSkuCode
    ? [correction.productSkuCode, correction.productSkuName].filter(Boolean).join(' · ')
    : correction?.productSkuId ? `SKU #${correction.productSkuId}` : '-';
  const warehouseLabel = correction?.warehouseCode
    ? [correction.warehouseCode, correction.warehouseName].filter(Boolean).join(' · ')
    : correction?.warehouseId ? `#${correction.warehouseId}` : '-';
  const locationLabel = correction?.locationCode
    ?? (correction?.locationId ? `#${correction.locationId}` : '-');

  const actions = (
    <Space wrap>
      {correction?.status === 'DRAFT' && canAdjust && draftApprovalMode === 'NONE' ? (
        <Button danger icon={<ThunderboltOutlined />} loading={previewLoading} onClick={() => onApply(correction)} type="primary">
          {t('corrections.actions.previewApply')}
        </Button>
      ) : null}
      {correction?.status === 'DRAFT' && canAdjust && draftApprovalMode !== undefined && draftApprovalMode !== 'NONE' ? (
        <Button icon={<SendOutlined />} onClick={() => onAction('submit', correction)} type="primary">{t('corrections.actions.submit')}</Button>
      ) : null}
      {correction?.status === 'DRAFT' && canAdjust && draftApprovalMode === undefined ? (
        <Button disabled loading={policyLoading}>{t('corrections.actions.loadingPolicy')}</Button>
      ) : null}
      {correction?.status === 'PENDING_REVIEW' && canReview ? (
        <Button icon={<CheckOutlined />} onClick={() => onAction('review', correction)} type="primary">{t('corrections.actions.review')}</Button>
      ) : null}
      {correction?.status === 'PENDING_REVIEW' && canReject ? (
        <Button danger icon={<CloseOutlined />} onClick={() => onAction('reject', correction)}>{t('corrections.actions.reject')}</Button>
      ) : null}
      {correction?.status === 'PENDING_APPROVAL' && canApprove ? (
        <Button danger icon={<CheckOutlined />} onClick={() => onAction('approve', correction)} type="primary">{t('corrections.actions.approve')}</Button>
      ) : null}
      {correction?.status === 'PENDING_APPROVAL' && canReject ? (
        <Button danger icon={<CloseOutlined />} onClick={() => onAction('reject', correction)}>{t('corrections.actions.reject')}</Button>
      ) : null}
      {correction?.status === 'APPROVED' && canAdjust ? (
        <Button danger icon={<ThunderboltOutlined />} loading={previewLoading} onClick={() => onApply(correction)} type="primary">{t('corrections.actions.previewApply')}</Button>
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
      <Descriptions
          bordered
          column={2}
          items={[
            { key: 'status', label: t('corrections.fields.status'), children: <Tag color={getStatusColor(correction?.status)}>{correction?.status ? t(`corrections.statuses.${correction.status}`) : '-'}</Tag> },
            { key: 'number', label: t('corrections.fields.correctionNo'), children: correction?.correctionNo ?? '-' },
            { key: 'warehouse', label: t('corrections.fields.warehouse'), children: warehouseLabel },
            { key: 'createdBy', label: t('corrections.fields.createdBy'), children: correction?.createdBy ? `#${correction.createdBy}` : '-' },
            { key: 'product', label: t('corrections.fields.product'), children: productLabel },
            { key: 'location', label: t('corrections.fields.location'), children: locationLabel },
            { key: 'batch', label: t('corrections.fields.batchCode'), children: correction?.batchCode ?? '-' },
            { key: 'expiry', label: t('corrections.fields.expiryDate'), children: correction?.expiryDate ?? '-' },
            { key: 'system', label: t('corrections.fields.systemQty'), children: correction?.systemQty ?? 0 },
            { key: 'counted', label: t('corrections.fields.countedQty'), children: correction?.countedQty ?? 0 },
            { key: 'adjustment', label: t('corrections.fields.adjustmentQty'), children: <Tag color={adjustment === 0 ? 'default' : adjustment > 0 ? 'success' : 'error'}>{adjustment > 0 ? `+${adjustment}` : adjustment}</Tag> },
            { key: 'order', label: t('corrections.fields.relatedSalesOrderId'), children: correction?.relatedSalesOrderId ?? '-' },
            { key: 'reason', label: t('corrections.fields.reasonCode'), children: correction?.reasonCode ? t(`corrections.reasons.${correction.reasonCode}`, { defaultValue: correction.reasonCode }) : '-', span: 2 },
            { key: 'detail', label: t('corrections.fields.reasonDetail'), children: correction?.reasonDetail ?? '-', span: 2 },
            {
              key: 'evidence',
              label: t('corrections.fields.evidenceUrl'),
              children: evidenceUrl
                ? safeEvidenceUrl
                  ? <a href={safeEvidenceUrl} rel="noreferrer" target="_blank">{evidenceUrl}</a>
                  : evidenceUrl
                : '-',
              span: 2,
            },
            { key: 'submissionComment', label: t('corrections.fields.submissionComment'), children: correction?.submissionComment ?? '-', span: 2 },
            { key: 'reviewComment', label: t('corrections.fields.reviewComment'), children: correction?.reviewComment ?? '-', span: 2 },
            { key: 'approvalComment', label: t('corrections.fields.approvalComment'), children: correction?.approvalComment ?? '-', span: 2 },
            {
              key: 'mode',
              label: t('corrections.fields.approvalMode'),
              children: correction?.approvalModeSnapshot
                ? t(`corrections.modes.${correction.approvalModeSnapshot}`)
                : t('corrections.fields.notSnapshotted'),
            },
            { key: 'postingId', label: t('corrections.fields.postingId'), children: correction?.postingId ?? '-' },
            { key: 'appliedBefore', label: t('corrections.fields.appliedQuantityBefore'), children: correction?.appliedQuantityBefore ?? '-' },
            { key: 'appliedAfter', label: t('corrections.fields.appliedQuantityAfter'), children: correction?.appliedQuantityAfter ?? '-' },
            { key: 'rejectionComment', label: t('corrections.fields.rejectionComment'), children: correction?.rejectionComment ?? '-', span: 2 },
            { key: 'rejectedBy', label: t('corrections.fields.rejectedBy'), children: correction?.rejectedBy ? `#${correction.rejectedBy}` : '-' },
            { key: 'rejectedAt', label: t('corrections.fields.rejectedAt'), children: formatDateTime(correction?.rejectedAt, i18n.language) },
            { key: 'created', label: t('common.createdAt'), children: formatDateTime(correction?.createdAt, i18n.language) },
            { key: 'updated', label: t('common.updatedAt'), children: formatDateTime(correction?.updatedAt, i18n.language) },
          ]}
          size="small"
      />
    </Drawer>
  );
}
