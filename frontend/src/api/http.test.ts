import MockAdapter from 'axios-mock-adapter';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createHttpClient, resetSharedRefreshPromiseForTests } from '@/api/http';

describe('401 refresh handling', () => {
  beforeEach(() => {
    resetSharedRefreshPromiseForTests();
  });

  it('clears auth state when refresh fails after 401', async () => {
    const onUnauthorized = vi.fn();
    const refreshAccessToken = vi.fn(async () => {
      throw new Error('refresh failed');
    });

    const client = createHttpClient({
      getAccessToken: () => 'expired-token',
      refreshAccessToken,
      onUnauthorized,
    });

    const mock = new MockAdapter(client);
    mock.onGet('/bookings').reply(401, {
      code: 'UNAUTHORIZED',
      message: 'unauthorized',
      details: [],
    });

    await expect(client.get('/bookings')).rejects.toThrow('refresh failed');
    expect(refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);

    mock.restore();
  });
});
