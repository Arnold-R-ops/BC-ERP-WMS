import { useTranslation } from 'react-i18next';

interface WarehouseOperationStepsProps {
  current: 0 | 1 | 2;
}

export function WarehouseOperationSteps({ current }: WarehouseOperationStepsProps): JSX.Element {
  const { t } = useTranslation();
  const steps = [
    t('mobile.steps.selectTask'),
    t('mobile.steps.verify'),
    t('mobile.steps.submit'),
  ];

  return (
    <ol aria-label={t('mobile.steps.title')} className="mobile-operation-steps">
      {steps.map((label, index) => (
        <li
          aria-current={current === index ? 'step' : undefined}
          className={`${index < current ? 'is-complete' : ''}${index === current ? ' is-current' : ''}`}
          key={label}
        >
          <span>{index + 1}</span>
          <strong>{label}</strong>
        </li>
      ))}
    </ol>
  );
}
