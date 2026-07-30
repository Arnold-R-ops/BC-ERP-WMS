import { Alert, Form, Input, Modal, Radio } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { ReviewStocktakePayload, StocktakeTask } from '../../api/stocktake';

interface StocktakeReviewModalProps {
  loading: boolean;
  onCancel: () => void;
  onConfirm: (payload: ReviewStocktakePayload) => Promise<void>;
  open: boolean;
  task?: StocktakeTask;
}

export function StocktakeReviewModal({
  loading,
  onCancel,
  onConfirm,
  open,
  task,
}: StocktakeReviewModalProps): JSX.Element {
  const [form] = Form.useForm<ReviewStocktakePayload>();
  const { t } = useTranslation();
  const approved = Form.useWatch('approved', form);

  useEffect(() => {
    if (open) form.setFieldsValue({ approved: true, comment: '' });
    else form.resetFields();
  }, [form, open]);

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      destroyOnHidden
      okButtonProps={{ danger: approved === true }}
      okText={t('stocktake.actions.submitReview')}
      onCancel={onCancel}
      onOk={() => form.submit()}
      open={open}
      title={t('stocktake.review.title', { taskNo: task?.taskNo ?? '-' })}
    >
      <Alert
        description={approved === false ? t('stocktake.review.rejectNotice') : t('stocktake.review.approveNotice')}
        message={t('stocktake.review.noticeTitle')}
        showIcon
        type={approved === false ? 'info' : 'warning'}
      />
      <Form form={form} layout="vertical" onFinish={onConfirm} style={{ marginTop: 16 }}>
        <Form.Item label={t('stocktake.review.result')} name="approved" rules={[{ required: true }]}>
          <Radio.Group
            options={[
              { label: t('stocktake.review.approve'), value: true },
              { label: t('stocktake.review.reject'), value: false },
            ]}
          />
        </Form.Item>
        <Form.Item
          label={t('workflow.comment')}
          name="comment"
          rules={[{ required: true, whitespace: true, message: t('stocktake.validation.commentRequired') }]}
        >
          <Input.TextArea maxLength={500} rows={4} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
