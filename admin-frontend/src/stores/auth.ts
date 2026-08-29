import { defineStore } from 'pinia';
import {
  beginLogin,
  beginLogout,
  completeLogin,
  completeLogout,
  removeSession,
  restoreSession,
  type AdminSession,
} from '@/security/oidc';
import { revokeAccessToken } from '@/api/auth';

export type LogoutResult = 'local-fallback' | 'redirected';

export class AdminRoleRequiredError extends Error {
  constructor() {
    super('当前账号没有管理员权限');
    this.name = 'AdminRoleRequiredError';
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    expiresAt: null as string | null,
    subject: null as string | null,
    studentNumber: null as string | null,
    roles: [] as string[],
    initialized: false,
    initializing: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    isAdmin: (state) => state.roles.includes('ADMIN'),
  },
  actions: {
    applySession(session: AdminSession) {
      if (!session.roles.includes('ADMIN')) throw new AdminRoleRequiredError();
      this.accessToken = session.accessToken;
      this.expiresAt = session.expiresAt;
      this.subject = session.subject;
      this.studentNumber = session.studentNumber;
      this.roles = session.roles;
    },
    clearSession() {
      this.accessToken = null;
      this.expiresAt = null;
      this.subject = null;
      this.studentNumber = null;
      this.roles = [];
    },
    async login(returnTo = '/vehicles') {
      await beginLogin(returnTo);
    },
    async completeLogin(): Promise<string> {
      try {
        const result = await completeLogin();
        this.applySession(result.session);
        this.initialized = true;
        return result.returnTo;
      } catch (error) {
        await removeSession().catch(() => undefined);
        this.clearSession();
        throw error;
      }
    },
    async initialize() {
      if (this.initialized || this.initializing) return;
      this.initializing = true;
      try {
        const session = await restoreSession();
        if (session) this.applySession(session);
      } catch {
        await removeSession().catch(() => undefined);
        this.clearSession();
      } finally {
        this.initializing = false;
        this.initialized = true;
      }
    },
    async logout(): Promise<LogoutResult> {
      if (this.accessToken) {
        try {
          await revokeAccessToken(this.accessToken);
        } catch {
          // Continue with RP-Initiated Logout so the browser session and
          // local credentials are still cleared when IAM is unavailable.
        }
      }
      try {
        if (await beginLogout()) return 'redirected';
      } catch {
        await removeSession().catch(() => undefined);
      }
      this.clearSession();
      this.initialized = true;
      return 'local-fallback';
    },
    async completeLogout() {
      try {
        await completeLogout();
      } finally {
        this.clearSession();
        this.initialized = true;
      }
    },
  },
});
