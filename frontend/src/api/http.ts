import { readAuthSession } from '../auth/storage';

export const AUTH_UNAUTHORIZED_EVENT = 'wms:auth:unauthorized';
export const AUTH_FORBIDDEN_EVENT = 'wms:auth:forbidden';

export interface ApiErrorBody {
  errorKey?: string;
  message?: string;
  params?: Record<string, unknown> & { message?: string };
  timestamp?: string;
  path?: string;
  status?: number;
}

export class ApiError extends Error {
  readonly status: number;
  readonly errorKey?: string;
  readonly body?: ApiErrorBody;

  constructor(status: number, body?: ApiErrorBody) {
    const backendMessage = body?.message ?? body?.params?.message;
    super(backendMessage ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.errorKey = body?.errorKey;
    this.body = body;
  }
}

interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  suppressAuthEvents?: boolean;
}

interface ApiRawRequestOptions extends Omit<RequestInit, 'body'> {
  body?: BodyInit;
  suppressAuthEvents?: boolean;
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | undefined> {
  const text = await response.text();
  if (!text) {
    return undefined;
  }

  try {
    return JSON.parse(text) as ApiErrorBody;
  } catch {
    return { message: text };
  }
}

function buildHeaders(headersInit?: HeadersInit): Headers {
  const headers = new Headers(headersInit);
  const session = readAuthSession();

  if (session?.token) {
    headers.set('Authorization', `${session.tokenType} ${session.token}`);
  }

  return headers;
}

async function throwForError(response: Response, suppressAuthEvents?: boolean): Promise<void> {
  if (response.ok) {
    return;
  }

  const body = await readErrorBody(response);
  const error = new ApiError(response.status, body);

  if (!suppressAuthEvents) {
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT, { detail: error }));
    } else if (response.status === 403) {
      window.dispatchEvent(new CustomEvent(AUTH_FORBIDDEN_EVENT, { detail: error }));
    }
  }

  throw error;
}

export async function apiRequest<TResponse>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<TResponse> {
  const headers = buildHeaders(options.headers);

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(path, {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  await throwForError(response, options.suppressAuthEvents);

  if (response.status === 204) {
    return undefined as TResponse;
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as TResponse;
}

export async function apiRawRequest<TResponse>(
  path: string,
  options: ApiRawRequestOptions = {},
): Promise<TResponse> {
  const response = await fetch(path, {
    ...options,
    headers: buildHeaders(options.headers),
  });

  await throwForError(response, options.suppressAuthEvents);
  if (response.status === 204) {
    return undefined as TResponse;
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as TResponse;
}

export async function apiBlobRequest(
  path: string,
  options: Omit<ApiRawRequestOptions, 'body'> = {},
): Promise<Blob> {
  const response = await fetch(path, {
    ...options,
    headers: buildHeaders(options.headers),
  });

  await throwForError(response, options.suppressAuthEvents);
  return response.blob();
}
