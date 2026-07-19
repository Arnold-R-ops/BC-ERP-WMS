import {
  ModalForm,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { Alert } from 'antd';
import { useTranslation } from 'react-i18next';
import type { Category } from '../../api/categories';

export interface CategoryFormValues {
  categoryCode: string;
  categoryName: string;
  parentId?: number;
  sortOrder?: number;
  description?: string;
}

interface CategoryFormModalProps {
  category?: Category;
  defaultParentId?: number;
  open: boolean;
  rootCategories: Category[];
  submitting: boolean;
  onClose: () => void;
  onSubmit: (values: CategoryFormValues) => Promise<boolean>;
}

export function CategoryFormModal({
  category,
  defaultParentId,
  open,
  rootCategories,
  submitting,
  onClose,
  onSubmit,
}: CategoryFormModalProps): JSX.Element {
  const { t } = useTranslation();
  const editing = category?.id !== undefined;
  const isRoot = editing && category?.parentId === undefined;
  const parentRequired = Boolean(defaultParentId || (editing && category?.parentId));

  return (
    <ModalForm<CategoryFormValues>
      key={category?.id ?? `new-category-${defaultParentId ?? 'root'}`}
      initialValues={{
        categoryCode: category?.categoryCode ?? '',
        categoryName: category?.categoryName ?? '',
        parentId: category?.parentId ?? defaultParentId,
        sortOrder: category?.sortOrder ?? 0,
        description: category?.description,
      }}
      modalProps={{ destroyOnHidden: true, maskClosable: false }}
      onFinish={onSubmit}
      onOpenChange={(nextOpen) => { if (!nextOpen) onClose(); }}
      open={open}
      submitter={{
        searchConfig: { resetText: t('common.cancel'), submitText: t('common.save') },
        submitButtonProps: { loading: submitting },
      }}
      title={editing ? t('categories.form.editTitle') : t('categories.form.createTitle')}
      width={600}
    >
      <Alert message={t('categories.form.twoLevelHint')} showIcon type="info" />
      <ProFormText
        disabled={editing}
        fieldProps={{ maxLength: 50 }}
        label={t('categories.fields.code')}
        name="categoryCode"
        rules={[
          { required: true, message: t('categories.validation.codeRequired') },
          { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,49}$/, message: t('categories.validation.codePattern') },
        ]}
      />
      <ProFormText
        fieldProps={{ maxLength: 100 }}
        label={t('categories.fields.name')}
        name="categoryName"
        rules={[{ required: true, message: t('categories.validation.nameRequired') }]}
      />
      <ProFormSelect
        disabled={Boolean(isRoot)}
        label={t('categories.fields.parent')}
        name="parentId"
        options={rootCategories
          .filter((root) => root.id !== category?.id && root.enabled !== false)
          .map((root) => ({ label: `${root.categoryName ?? '-'} (${root.categoryCode ?? '-'})`, value: root.id }))}
        placeholder={t('categories.form.parentPlaceholder')}
        rules={parentRequired ? [{ required: true, message: t('categories.validation.parentRequired') }] : undefined}
      />
      <ProFormDigit fieldProps={{ min: 0, precision: 0 }} label={t('categories.fields.sortOrder')} name="sortOrder" />
      <ProFormTextArea fieldProps={{ maxLength: 500, showCount: true }} label={t('categories.fields.description')} name="description" />
    </ModalForm>
  );
}
