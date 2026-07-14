import type { TFunction } from 'i18next';
import { ApiError } from './http';

export function getErrorMessage(error: unknown, t: TFunction): string {
  if (error instanceof ApiError) {
    if (error.errorKey) {
      const translationKey = `errors.${error.errorKey}`;
      const translated = t(translationKey, { defaultValue: '' });
      if (translated) {
        return translated;
      }
    }

    const backendMessage = error.body?.message ?? error.body?.params?.message;
    return backendMessage ?? t('errors.requestFailed');
  }

  return error instanceof Error && error.message
    ? error.message
    : t('errors.requestFailed');
}
