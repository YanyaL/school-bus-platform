import { describe, expect, it } from 'vitest';
import {
  ApiClientError,
  formatValidationDetails,
  isApiErrorResponse,
  parseApiError,
  resolveUserMessage,
} from '@/types/api';

describe('api response parsing', () => {
  it('parses backend error response', () => {
    const error = parseApiError({
      response: {
        status: 409,
        data: {
          code: 'SEAT_UNAVAILABLE',
          message: '座位已被占用',
          details: [{ field: 'seatNumber', reason: 'unavailable' }],
          traceId: 'trace-1',
          timestamp: '2026-08-15T00:00:00.000Z',
        },
      },
    });

    expect(error).toBeInstanceOf(ApiClientError);
    expect(error.code).toBe('SEAT_UNAVAILABLE');
    expect(error.status).toBe(409);
    expect(error.traceId).toBe('trace-1');
    expect(error.message).toBe('座位已被占用');
  });

  it('maps rate limit message', () => {
    const error = new ApiClientError('too many', 'RATE_LIMITED', 429);
    expect(resolveUserMessage(error)).toBe('请求过于频繁，请稍后重试');
  });

  it('detects api error response shape', () => {
    expect(
      isApiErrorResponse({
        code: 'VALIDATION_ERROR',
        message: 'invalid',
        details: [],
      }),
    ).toBe(true);
  });

  it('formats validation details', () => {
    expect(formatValidationDetails([{ field: 'password', reason: 'too short' }])).toBe(
      'password: too short',
    );
  });
});
