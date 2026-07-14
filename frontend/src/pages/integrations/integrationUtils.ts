export function isNoSkuExternal(externalSku?: string): boolean {
  return externalSku?.startsWith('NOSKU::') ?? false;
}

export function formatRawPayload(payload?: string): string {
  if (!payload) {
    return '-';
  }

  try {
    return JSON.stringify(JSON.parse(payload) as unknown, null, 2);
  } catch {
    return payload;
  }
}

export function getChannelColor(channel?: string): string {
  switch (channel?.toUpperCase()) {
    case 'SHOPIFY': return 'green';
    case 'WECHAT': return 'cyan';
    case 'MANUAL': return 'default';
    default: return 'blue';
  }
}

export function getEventStatusColor(status?: string): string {
  switch (status) {
    case 'PROCESSED': return 'success';
    case 'FAILED': return 'error';
    case 'MANUAL_REVIEW': return 'warning';
    case 'SKIPPED': return 'default';
    default: return 'processing';
  }
}
