import { defineStore } from 'pinia';
import { createAuthApi } from '@/api/auth';
import { createBookingsApi } from '@/api/bookings';
import { createHttpClient } from '@/api/http';
import { createTripsApi } from '@/api/trips';
import type { AuthSession, LoginRequest } from '@/types/auth';
import { REFRESH_TOKEN_STORAGE_KEY, STUDENT_NUMBER_STORAGE_KEY } from '@/types/auth';

interface AuthState {
  accessToken: string | null;
  userId: number | null;
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
  userId: number;
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
      this.userId = session.userId;
      this.studentNumber = session.studentNumber;
      this.roles = session.roles;
      writeStoredRefreshToken(session.refreshToken);
      writeStoredStudentNumber(session.studentNumber);
    },

    clearSession() {
      this.accessToken = null;
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

    async refreshSession(): Promise<string> {
      const refreshToken = readStoredRefreshToken();
      if (!refreshToken) {
        this.clearSession();
        throw new Error('缺少 refresh token');
      }

      const { auth } = this.createApis();
      const response = await auth.refresh({ refreshToken });
      const currentStudentNumber = this.studentNumber ?? '';
      const currentUserId = this.userId ?? 0;
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

      const refreshToken = readStoredRefreshToken();
      if (!refreshToken) {
        this.initialized = true;
        return;
      }

      this.initializing = true;
      try {
        await this.refreshSession();
        const { auth } = this.createApis();
        const me = await auth.me();
        this.userId = me.userId;
        this.roles = me.roles;
      } catch {
        this.clearSession();
      } finally {
        this.initializing = false;
        this.initialized = true;
      }
    },

    async logout(): Promise<void> {
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
