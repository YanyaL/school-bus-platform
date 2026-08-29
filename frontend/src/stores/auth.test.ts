import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import {
  REFRESH_TOKEN_STORAGE_KEY,
  STUDENT_NUMBER_STORAGE_KEY,
  type LoginResponse,
} from '@/types/auth';
import { useAuthStore } from '@/stores/auth';
import type { SsoCallbackResult, SsoSession } from '@/security/oidc';

const ssoMocks = vi.hoisted(() => ({
  beginSsoLogin: vi.fn<(returnTo: string) => Promise<void>>(async () => undefined),
  beginSsoLogout: vi.fn<() => Promise<boolean>>(async () => true),
  completeSsoLogin: vi.fn<() => Promise<SsoCallbackResult>>(),
  completeSsoLogout: vi.fn<() => Promise<void>>(async () => undefined),
  restoreSsoSession: vi.fn<() => Promise<SsoSession | null>>(async () => null),
  removeSsoSession: vi.fn<() => Promise<void>>(async () => undefined),
}));
const authApiMocks = vi.hoisted(() => ({
  logout: vi.fn(async () => undefined),
}));

vi.mock('@/security/oidc', () => ssoMocks);

const loginResponse: LoginResponse = {
  userId: '1001',
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
    logout: authApiMocks.logout,
    me: vi.fn(async () => ({ userId: '1001', roles: ['STUDENT'] })),
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
    sessionStorage.clear();
    ssoMocks.completeSsoLogin.mockReset();
    ssoMocks.beginSsoLogout.mockReset();
    ssoMocks.beginSsoLogout.mockResolvedValue(true);
    ssoMocks.completeSsoLogout.mockReset();
    ssoMocks.completeSsoLogout.mockResolvedValue(undefined);
    ssoMocks.restoreSsoSession.mockResolvedValue(null);
    ssoMocks.removeSsoSession.mockClear();
    authApiMocks.logout.mockClear();
  });

  it('applies an OIDC callback without persisting a refresh token', async () => {
    ssoMocks.completeSsoLogin.mockResolvedValue({
      returnTo: '/bookings',
      session: {
        accessToken: 'sso-access-token',
        accessTokenExpiresAt: '2100-01-01T00:00:00.000Z',
        userId: '1000001',
        studentNumber: 'S4789503',
        roles: ['STUDENT'],
      },
    });
    const store = useAuthStore();

    const returnTo = await store.completeSsoLogin();

    expect(returnTo).toBe('/bookings');
    expect(store.authMode).toBe('sso');
    expect(store.accessToken).toBe('sso-access-token');
    expect(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBeNull();
  });

  it('restores an unexpired OIDC session before legacy refresh', async () => {
    ssoMocks.restoreSsoSession.mockResolvedValue({
      accessToken: 'restored-sso-token',
      accessTokenExpiresAt: '2100-01-01T00:00:00.000Z',
      userId: '1000001',
      studentNumber: 'S4789503',
      roles: ['STUDENT'],
    });
    const store = useAuthStore();

    await store.initializeSession();

    expect(store.authMode).toBe('sso');
    expect(store.accessToken).toBe('restored-sso-token');
  });

  it('removes a partial OIDC user when callback processing fails', async () => {
    ssoMocks.completeSsoLogin.mockRejectedValue(new Error('invalid callback state'));
    const store = useAuthStore();

    await expect(store.completeSsoLogin()).rejects.toThrow('invalid callback state');

    expect(ssoMocks.removeSsoSession).toHaveBeenCalledOnce();
    expect(store.isAuthenticated).toBe(false);
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
    expect(localStorage.getItem(STUDENT_NUMBER_STORAGE_KEY)).toBe('S20260001');
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
    expect(localStorage.getItem(STUDENT_NUMBER_STORAGE_KEY)).toBeNull();
  });

  it('redirects an SSO session through the provider logout endpoint', async () => {
    const store = useAuthStore();
    store.applySsoSession({
      accessToken: 'sso-access-token',
      accessTokenExpiresAt: '2100-01-01T00:00:00.000Z',
      userId: '1000001',
      studentNumber: 'S4789503',
      roles: ['STUDENT'],
    });

    const result = await store.logout();

    expect(result).toBe('redirected');
    expect(authApiMocks.logout).toHaveBeenCalledOnce();
    expect(ssoMocks.beginSsoLogout).toHaveBeenCalledOnce();
  });

  it('falls back to local logout when no OIDC ID Token is available', async () => {
    ssoMocks.beginSsoLogout.mockResolvedValue(false);
    const store = useAuthStore();
    store.applySsoSession({
      accessToken: 'sso-access-token',
      accessTokenExpiresAt: '2100-01-01T00:00:00.000Z',
      userId: '1000001',
      studentNumber: 'S4789503',
      roles: ['STUDENT'],
    });

    const result = await store.logout();

    expect(result).toBe('local-fallback');
    expect(store.isAuthenticated).toBe(false);
  });

  it('clears local state even when logout callback validation fails', async () => {
    ssoMocks.completeSsoLogout.mockRejectedValue(new Error('invalid logout state'));
    const store = useAuthStore();
    store.applySsoSession({
      accessToken: 'sso-access-token',
      accessTokenExpiresAt: '2100-01-01T00:00:00.000Z',
      userId: '1000001',
      studentNumber: 'S4789503',
      roles: ['STUDENT'],
    });

    await expect(store.completeSsoLogout()).rejects.toThrow('invalid logout state');

    expect(store.isAuthenticated).toBe(false);
  });

  it('restores the non-sensitive student number for display after reload', async () => {
    const firstStore = useAuthStore();
    await firstStore.login({
      studentNumber: 'S20260001',
      password: 'Password!2026',
    });

    setActivePinia(createPinia());
    const restoredStore = useAuthStore();

    expect(restoredStore.displayName).toBe('S20260001');
  });
});
