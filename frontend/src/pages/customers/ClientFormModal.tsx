import {
  ModalForm,
  ProFormDigit,
  ProFormDependency,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { MailOutlined, PhoneOutlined, ProfileOutlined, UserOutlined } from '@ant-design/icons';
import { Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Customer, CustomerPayload } from '../../api/masterData';

function InputIcon({ children }: { children: React.ReactNode }): JSX.Element {
  return <span className="client-input-icon">{children}</span>;
}

interface ClientFormModalProps {
  customer?: Customer;
  loading: boolean;
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: CustomerPayload) => Promise<boolean>;
}

export function ClientFormModal({
  customer,
  loading,
  open,
  onClose,
  onSubmit,
}: ClientFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('details');
  const editing = customer?.id !== undefined;

  useEffect(() => {
    if (open) setActiveTab('details');
  }, [customer?.id, open]);

  return (
    <ModalForm<CustomerPayload>
      key={customer?.id ?? 'new-client'}
      initialValues={{
        name: customer?.name ?? '',
        contact: customer?.contact ?? '',
        phone: customer?.phone ?? '',
        email: customer?.email ?? '',
        address: customer?.address ?? '',
        shippingSameAsBilling: customer?.shippingSameAsBilling ?? true,
        shippingAddress: customer?.shippingAddress ?? '',
        creditLimit: customer?.creditLimit ?? 0,
        vatRate: customer?.vatRate,
        secondaryTaxRate: customer?.secondaryTaxRate,
        vatNumber: customer?.vatNumber ?? '',
        isActive: customer?.isActive ?? true,
      }}
      modalProps={{
        centered: true,
        className: 'client-form-modal',
        destroyOnHidden: true,
        maskClosable: false,
        styles: {
          body: { overflowY: 'auto' },
        },
      }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: editing ? t('common.save') : t('common.create') },
        submitButtonProps: { loading },
      }}
      title={editing ? t('clients.form.editTitle') : t('clients.form.createTitle')}
      width={810}
    >
      <Tabs activeKey={activeTab} centered className="client-form-tabs" items={[
        { key: 'details', label: t('clients.form.tabs.details') },
        { key: 'tax', label: t('clients.form.tabs.tax') },
      ]} onChange={setActiveTab} />
      {activeTab === 'details' && <>
      <h2 className="client-form-section">{t('clients.form.sections.details')}</h2>
      <ProFormText
        fieldProps={{ maxLength: 100, prefix: <InputIcon><UserOutlined /></InputIcon> }}
        label={t('clients.fields.name')}
        name="name"
        rules={[{ required: true, message: t('clients.validation.nameRequired') }]}
      />
      <div className="client-form-two-columns">
        <ProFormText fieldProps={{ maxLength: 100, prefix: <InputIcon><MailOutlined /></InputIcon>, type: 'email' }} label={t('clients.fields.email')} name="email" />
        <ProFormText fieldProps={{ maxLength: 30, prefix: <InputIcon><PhoneOutlined /></InputIcon> }} label={t('clients.fields.phone')} name="phone" />
      </div>
      <ProFormText fieldProps={{ maxLength: 100 }} label={t('clients.fields.contact')} name="contact" />
      <ProFormDigit fieldProps={{ min: 0, precision: 2 }} label={t('clients.fields.creditLimit')} name="creditLimit" />
      <h2 className="client-form-section">{t('clients.form.sections.billingAddress')}</h2>
      <ProFormTextArea fieldProps={{ maxLength: 255, placeholder: t('clients.form.preview.addressPlaceholder'), showCount: true }} label={t('clients.fields.address')} name="address" rules={[{ max: 255 }]} />
      <h2 className="client-form-section">{t('clients.form.sections.shippingAddress')}</h2>
      <ProFormSwitch label={t('upgrade.sameAddress')} name="shippingSameAsBilling" />
      <ProFormDependency name={['shippingSameAsBilling']}>
        {({ shippingSameAsBilling }) => shippingSameAsBilling === false
          ? <ProFormTextArea fieldProps={{ maxLength: 255, showCount: true }} label={t('upgrade.shippingAddress')} name="shippingAddress" rules={[{ required: true, whitespace: true }, { max: 255 }]} />
          : null}
      </ProFormDependency>
      <ProFormSwitch label={t('clients.fields.active')} name="isActive" />
      </>}
      {activeTab === 'tax' && <>
        <h2 className="client-form-section">{t('clients.form.sections.customerTax')}</h2>
        <p className="client-form-hint">{t('clients.form.hints.customerTax')}</p>
        <div className="client-form-two-columns">
          <ProFormDigit fieldProps={{ min: 0, max: 100, precision: 2, addonAfter: '%' }} label={t('clients.form.tax.vat')} name="vatRate" />
          <ProFormDigit fieldProps={{ min: 0, max: 100, precision: 2, addonAfter: '%' }} label={t('clients.form.tax.secondaryTax')} name="secondaryTaxRate" />
        </div>
        <h2 className="client-form-section">{t('clients.form.sections.vatRegistration')}</h2>
        <p className="client-form-hint">{t('clients.form.hints.vatRegistration')}</p>
        <ProFormText fieldProps={{ maxLength: 100, placeholder: t('clients.form.tax.vatNumberPlaceholder'), prefix: <InputIcon><ProfileOutlined /></InputIcon> }} label={t('clients.form.tax.vatNumber')} name="vatNumber" />
      </>}
    </ModalForm>
  );
}
