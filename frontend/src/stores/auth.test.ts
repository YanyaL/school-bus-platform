import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { REFRESH_TOKEN_STORAGE_KEY, type LoginResponse } from '@/types/auth';
import { useAuthStore } from '@/stores/auth';

const loginResponse: LoginResponse = {
  userId: 1001,
  studentNumber: 'S20260001',
  roles: ['STUDENT'],
  tokenType: 'Bearer',
  accessToken: 'access-token',
  accessTokenExpiresAt: '2026-08-15T01:00:00.000Z',
  refreshToken: 'refresh-token',
  refreshTokenExpiresAt: '2026-08-22T01:00:00.000Z',
};

vi.mock('@/api/auth', () => ({
  createAuthApi: vi.fn(() => ({
    login: vi.fn(async () => loginResponse),
    refresh: vi.fn(async () => ({
      tokenType: 'Bearer',
      accessToken: 'new-access-token',
      accessTokenExpiresAt: '2026-08-15T02:00:00.000Z',
      refreshToken: 'new-refresh-token',
      refreshTokenExpiresAt: '2026-08-23T01:00:00.000Z',
    })),
    logout: vi.fn(async () => undefined),
    me: vi.fn(async () => ({ userId: 1001, roles: ['STUDENT'] })),
    register: vi.fn(),
  })),
}));

vi.mock('@/api/http', () => ({
  createHttpClient: vi.fn(() => ({
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  })),
  createRefreshClient: vi.fn(() => ({})),
}));

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  it('stores login session in memory and refresh token in localStorage', async () => {
    const store = useAuthStore();

    await store.login({
      studentNumber: 'S20260001',
      password: 'Password!2026',
    });

    expect(store.isAuthenticated).toBe(true);
    expect(store.accessToken).toBe('access-token');
    expect(store.studentNumber).toBe('S20260001');
    expect(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBe('refresh-token');
  });

  it('clears session on logout', async () => {
    const store = useAuthStore();
    await store.login({
      studentNumber: 'S20260001',
      password: 'Password!2026',
    });

    await store.logout();

    expect(store.isAuthenticated).toBe(false);
    expect(store.accessToken).toBeNull();
    expect(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBeNull();
  });
});
