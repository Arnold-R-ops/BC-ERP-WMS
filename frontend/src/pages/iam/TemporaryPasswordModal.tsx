import { CheckCircleOutlined, CopyOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Input, Modal, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ResetPasswordResult } from '../../api/iam';

interface TemporaryPasswordModalProps {
  open: boolean;
  result?: ResetPasswordResult;
  onClose: () => void;
  onCopy: (value: string) => Promise<void>;
}

export function TemporaryPasswordModal({ open, result, onClose, onCopy }: TemporaryPasswordModalProps): JSX.Element {
  const { t } = useTranslation();
  const password = result?.temporaryPassword ?? '';
  return (
    <Modal
      cancelButtonProps={{ style: { display: 'none' } }}
      destroyOnHidden
      okText={t('iam.users.reset.done')}
      onCancel={onClose}
      onOk={onClose}
      open={open}
      title={t('iam.users.reset.resultTitle')}
      width={620}
    >
      <Alert
        className="form-notice"
        description={t('iam.users.reset.oneTimeNotice')}
        icon={<CheckCircleOutlined />}
        showIcon
        type="success"
      />
      <Descriptions column={1} size="small">
        <Descriptions.Item label={t('iam.users.fields.username')}>{result?.username}</Descriptions.Item>
        <Descriptions.Item label={t('iam.users.fields.temporaryPassword')}>
          <Space.Compact block>
            <Input readOnly value={password} />
            <Button
              aria-label={t('common.copied')}
              icon={<CopyOutlined />}
              onClick={() => void onCopy(password)}
            />
          </Space.Compact>
        </Descriptions.Item>
      </Descriptions>
    </Modal>
  );
}
