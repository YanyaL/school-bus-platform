export interface FieldErrorDetail {
  field: string;
  reason: string;
}

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  traceId: string;
  timestamp: string;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  details: FieldErrorDetail[];
  traceId: string;
  timestamp: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export class ApiClientError extends Error {
  readonly code: string;
  readonly status: number;
  readonly traceId?: string;
  readonly details: FieldErrorDetail[];

  constructor(
    message: string,
    code: string,
    status: number,
    traceId?: string,
    details: FieldErrorDetail[] = [],
  ) {
    super(message);
    this.name = 'ApiClientError';
    this.code = code;
    this.status = status;
    this.traceId = traceId;
    this.details = details;
  }
}

export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    Array.isArray(candidate.details)
  );
}

export function parseApiError(error: unknown, fallbackStatus = 500): ApiClientError {
  if (error instanceof ApiClientError) {
    return error;
  }

  if (typeof error === 'object' && error !== null && 'response' in error) {
    const axiosError = error as {
      response?: { status?: number; data?: unknown };
      message?: string;
    };
    const status = axiosError.response?.status ?? fallbackStatus;
    const data = axiosError.response?.data;

    if (isApiErrorResponse(data)) {
      return new ApiClientError(
        data.message,
        data.code,
        status,
        data.traceId,
        data.details,
      );
    }

    return new ApiClientError(axiosError.message ?? '请求失败', 'UNKNOWN', status);
  }

  if (error instanceof Error) {
    return new ApiClientError(error.message, 'UNKNOWN', fallbackStatus);
  }

  return new ApiClientError('请求失败', 'UNKNOWN', fallbackStatus);
}

export function resolveUserMessage(error: ApiClientError): string {
  if (error.status === 429 || error.code === 'RATE_LIMITED') {
    return '请求过于频繁，请稍后重试';
  }
  if (error.status === 403 || error.code === 'FORBIDDEN') {
    return '无权访问';
  }
  if (error.message) {
    return error.message;
  }
  return '请求失败，请稍后重试';
}

export function formatValidationDetails(details: FieldErrorDetail[]): string {
  return details.map((item) => `${item.field}: ${item.reason}`).join('；');
}
