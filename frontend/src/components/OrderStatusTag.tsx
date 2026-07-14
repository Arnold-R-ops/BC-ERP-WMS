import { Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import { getStatusColor, type WorkflowDomain } from '../pages/workflowUtils';

interface OrderStatusTagProps {
  description?: string;
  domain: WorkflowDomain;
  status?: string;
}

export function OrderStatusTag({ description, domain, status }: OrderStatusTagProps): JSX.Element {
  const { t } = useTranslation();
  const text = status
    ? t(`${domain}.statuses.${status}`, { defaultValue: description ?? status })
    : '-';

  return <Tag color={getStatusColor(status)}>{text}</Tag>;
}
