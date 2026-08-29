import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, vi } from 'vitest';
import type { AdminSession } from '@/security/oidc';

const oidc = vi.hoisted(() => ({
  beginLogin: vi.fn(),
  beginLogout: vi.fn(),
  completeLogin: vi.fn(),
  completeLogout: vi.fn(),
  removeSession: vi.fn(),
  restoreSession: vi.fn(),
}));
const authApi = vi.hoisted(() => ({
  revokeAccessToken: vi.fn(),
}));

vi.mock('@/security/oidc', () => oidc);
vi.mock('@/api/auth', () => authApi);

import { AdminRoleRequiredError, useAuthStore } from './auth';

const adminSession: AdminSession = {
  accessToken: 'access-token',
  expiresAt: '2030-01-01T00:00:00.000Z',
  subject: '1000001',
  studentNumber: 'S4789503',
  roles: ['STUDENT', 'ADMIN'],
};

describe('admin auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    oidc.removeSession.mockResolvedValue(undefined);
    authApi.revokeAccessToken.mockResolvedValue(undefined);
  });

  it('accepts an ADMIN session returned by the OIDC callback', async () => {
    oidc.completeLogin.mockResolvedValue({
      session: adminSession,
      returnTo: '/trips',
    });
    const auth = useAuthStore();

    await expect(auth.completeLogin()).resolves.toBe('/trips');
    expect(auth.isAuthenticated).toBe(true);
    expect(auth.isAdmin).toBe(true);
    expect(auth.subject).toBe('1000001');
  });

  it('rejects a valid student token that lacks the ADMIN role', async () => {
    oidc.completeLogin.mockResolvedValue({
      session: { ...adminSession, roles: ['STUDENT'] },
      returnTo: '/vehicles',
    });
    const auth = useAuthStore();

    await expect(auth.completeLogin()).rejects.toBeInstanceOf(AdminRoleRequiredError);
    expect(oidc.removeSession).toHaveBeenCalledOnce();
    expect(auth.isAuthenticated).toBe(false);
  });

  it('restores an existing administrator session', async () => {
    oidc.restoreSession.mockResolvedValue(adminSession);
    const auth = useAuthStore();

    await auth.initialize();

    expect(auth.initialized).toBe(true);
    expect(auth.studentNumber).toBe('S4789503');
    expect(auth.isAdmin).toBe(true);
  });

  it('uses the IAM end-session endpoint when an ID token exists', async () => {
    oidc.beginLogout.mockResolvedValue(true);
    const auth = useAuthStore();
    auth.applySession(adminSession);

    await expect(auth.logout()).resolves.toBe('redirected');
    expect(authApi.revokeAccessToken).toHaveBeenCalledWith('access-token');
    expect(oidc.beginLogout).toHaveBeenCalledOnce();
  });
});
