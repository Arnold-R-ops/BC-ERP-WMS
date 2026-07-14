import { UploadOutlined } from '@ant-design/icons';
import { Button, Form, Input, Modal, Upload } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

export interface PurchaseImportValues {
  supplier: string;
  expectedDate?: string;
  file: File;
}

interface PurchaseImportModalProps {
  loading: boolean;
  open: boolean;
  onCancel: () => void;
  onConfirm: (values: PurchaseImportValues) => Promise<void> | void;
}

export function PurchaseImportModal({ loading, open, onCancel, onConfirm }: PurchaseImportModalProps): JSX.Element {
  const [form] = Form.useForm<Omit<PurchaseImportValues, 'file'>>();
  const [file, setFile] = useState<File>();
  const { t } = useTranslation();

  useEffect(() => {
    if (open) {
      form.resetFields();
      setFile(undefined);
    }
  }, [form, open]);

  const submit = async (): Promise<void> => {
    const values = await form.validateFields();
    if (!file) return;
    await onConfirm({ ...values, file });
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okButtonProps={{ disabled: !file }}
      okText={t('purchasing.actions.import')}
      onCancel={onCancel}
      onOk={() => void submit()}
      open={open}
      title={t('purchasing.import.title')}
    >
      <Form form={form} layout="vertical">
        <Form.Item label={t('purchasing.fields.supplier')} name="supplier" rules={[{ required: true, message: t('purchasing.validation.supplierRequired') }]}>
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item label={t('purchasing.fields.expectedDate')} name="expectedDate"><Input type="date" /></Form.Item>
        <Form.Item label={t('purchasing.import.file')} required>
          <Upload
            accept=".xlsx"
            beforeUpload={(nextFile) => { setFile(nextFile as File); return false; }}
            fileList={file ? [{ uid: 'purchase-import', name: file.name, status: 'done' }] : []}
            maxCount={1}
            onRemove={() => { setFile(undefined); return true; }}
          >
            <Button icon={<UploadOutlined />}>{t('purchasing.import.chooseFile')}</Button>
          </Upload>
        </Form.Item>
      </Form>
    </Modal>
  );
}
