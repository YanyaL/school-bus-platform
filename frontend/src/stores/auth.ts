import { defineStore } from 'pinia';
import { createAuthApi } from '@/api/auth';
import { createBookingsApi } from '@/api/bookings';
import { createHttpClient } from '@/api/http';
import { createTripsApi } from '@/api/trips';
import type { AuthSession, LoginRequest } from '@/types/auth';
import { REFRESH_TOKEN_STORAGE_KEY, STUDENT_NUMBER_STORAGE_KEY } from '@/types/auth';
import {
  beginSsoLogin as redirectToSso,
  completeSsoLogin as processSsoCallback,
  removeSsoSession,
  restoreSsoSession,
  type SsoSession,
} from '@/security/oidc';

type AuthMode = 'legacy' | 'sso';

interface AuthState {
  accessToken: string | null;
  accessTokenExpiresAt: string | null;
  authMode: AuthMode | null;
  userId: string | null;
  studentNumber: string | null;
  roles: string[];
  initialized: boolean;
  initializing: boolean;
}

function readStoredRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
}

function writeStoredRefreshToken(token: string | null): void {
  if (!token) {
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
    return;
  }
  localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, token);
}

function readStoredStudentNumber(): string | null {
  return localStorage.getItem(STUDENT_NUMBER_STORAGE_KEY);
}

function writeStoredStudentNumber(studentNumber: string | null): void {
  if (!studentNumber) {
    localStorage.removeItem(STUDENT_NUMBER_STORAGE_KEY);
    return;
  }
  localStorage.setItem(STUDENT_NUMBER_STORAGE_KEY, studentNumber);
}

function buildSessionFromLogin(response: {
  userId: string;
  studentNumber: string;
  roles: string[];
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}): AuthSession {
  return {
    userId: response.userId,
    studentNumber: response.studentNumber,
    roles: response.roles,
    accessToken: response.accessToken,
    accessTokenExpiresAt: response.accessTokenExpiresAt,
    refreshToken: response.refreshToken,
    refreshTokenExpiresAt: response.refreshTokenExpiresAt,
  };
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    accessTokenExpiresAt: null,
    authMode: null,
    userId: null,
    studentNumber: readStoredStudentNumber(),
    roles: [],
    initialized: false,
    initializing: false,
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    displayName: (state) => state.studentNumber || '已登录',
  },

  actions: {
    createHttpClient() {
      return createHttpClient({
        getAccessToken: () => this.accessToken,
        refreshAccessToken: () => this.refreshSession(),
        onUnauthorized: () => {
          this.clearSession();
        },
      });
    },

    createApis() {
      const http = this.createHttpClient();
      return {
        auth: createAuthApi(http),
        trips: createTripsApi(http),
        bookings: createBookingsApi(http),
      };
    },

    applySession(session: AuthSession) {
      this.accessToken = session.accessToken;
      this.accessTokenExpiresAt = session.accessTokenExpiresAt;
      this.authMode = 'legacy';
      this.userId = session.userId;
      this.studentNumber = session.studentNumber;
      this.roles = session.roles;
      writeStoredRefreshToken(session.refreshToken);
      writeStoredStudentNumber(session.studentNumber);
    },

    applySsoSession(session: SsoSession) {
      this.accessToken = session.accessToken;
      this.accessTokenExpiresAt = session.accessTokenExpiresAt;
      this.authMode = 'sso';
      this.userId = session.userId;
      this.studentNumber = session.studentNumber;
      this.roles = session.roles;
      writeStoredRefreshToken(null);
      writeStoredStudentNumber(session.studentNumber);
    },

    clearSession() {
      this.accessToken = null;
      this.accessTokenExpiresAt = null;
      this.authMode = null;
      this.userId = null;
      this.studentNumber = null;
      this.roles = [];
      writeStoredRefreshToken(null);
      writeStoredStudentNumber(null);
    },

    async login(payload: LoginRequest): Promise<void> {
      const { auth } = this.createApis();
      const response = await auth.login(payload);
      this.applySession(buildSessionFromLogin(response));
      this.initialized = true;
    },

    async beginSsoLogin(returnTo = '/trips'): Promise<void> {
      await redirectToSso(returnTo);
    },

    async completeSsoLogin(): Promise<string> {
      try {
        const result = await processSsoCallback();
        this.applySsoSession(result.session);
        this.initialized = true;
        return result.returnTo;
      } catch (error) {
        try {
          await removeSsoSession();
        } catch {
          // 保留原始回调错误，清理失败不能覆盖根因
        }
        this.clearSession();
        throw error;
      }
    },

    async refreshSession(): Promise<string> {
      if (this.authMode === 'sso') {
        await removeSsoSession();
        this.clearSession();
        throw new Error('SSO access token 已过期，请重新登录');
      }
      const refreshToken = readStoredRefreshToken();
      if (!refreshToken) {
        this.clearSession();
        throw new Error('缺少 refresh token');
      }

      const { auth } = this.createApis();
      const response = await auth.refresh({ refreshToken });
      const currentStudentNumber = this.studentNumber ?? '';
      const currentUserId = this.userId ?? '';
      const currentRoles = this.roles.length > 0 ? this.roles : ['STUDENT'];

      this.applySession({
        userId: currentUserId,
        studentNumber: currentStudentNumber,
        roles: currentRoles,
        accessToken: response.accessToken,
        accessTokenExpiresAt: response.accessTokenExpiresAt,
        refreshToken: response.refreshToken,
        refreshTokenExpiresAt: response.refreshTokenExpiresAt,
      });

      if (!this.studentNumber || !this.userId) {
        const me = await auth.me();
        this.userId = me.userId;
        this.roles = me.roles;
      }

      return response.accessToken;
    },

    async initializeSession(): Promise<void> {
      if (this.initialized || this.initializing) {
        return;
      }

      this.initializing = true;
      try {
        const ssoSession = await restoreSsoSession();
        if (ssoSession) {
          this.applySsoSession(ssoSession);
          return;
        }

        const refreshToken = readStoredRefreshToken();
        if (!refreshToken) {
          return;
        }
        await this.refreshSession();
        const { auth } = this.createApis();
        const me = await auth.me();
        this.userId = me.userId;
        this.roles = me.roles;
      } catch {
        try {
          await removeSsoSession();
        } catch {
          // 初始化仍应结束，下一次登录会重新创建 OIDC 状态
        }
        this.clearSession();
      } finally {
        this.initializing = false;
        this.initialized = true;
      }
    },

    async logout(): Promise<void> {
      if (this.authMode === 'sso') {
        try {
          await removeSsoSession();
        } finally {
          this.clearSession();
          this.initialized = true;
        }
        return;
      }
      if (this.accessToken) {
        try {
          const { auth } = this.createApis();
          await auth.logout();
        } catch {
          // 无论后端是否成功，都清理本地状态
        }
      }
      this.clearSession();
      this.initialized = true;
    },
  },
});
