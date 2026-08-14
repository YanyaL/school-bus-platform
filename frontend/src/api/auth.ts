import type { AxiosInstance } from 'axios';
import type {
  CurrentUserResponse,
  LoginRequest,
  LoginResponse,
  RefreshTokenRequest,
  RefreshTokenResponse,
  RegisterAccountRequest,
  RegisterAccountResponse,
} from '@/types/auth';
import { createRefreshClient, unwrapApiResponse } from './http';

export function createAuthApi(http: AxiosInstance) {
  const refreshClient = createRefreshClient();

  return {
    async register(payload: RegisterAccountRequest): Promise<RegisterAccountResponse> {
      const response = await http.post('/accounts', payload);
      return unwrapApiResponse(response);
    },

    async login(payload: LoginRequest): Promise<LoginResponse> {
      const response = await http.post('/auth/login', payload);
      return unwrapApiResponse(response);
    },

    async refresh(payload: RefreshTokenRequest): Promise<RefreshTokenResponse> {
      const response = await refreshClient.post('/auth/refresh', payload);
      return unwrapApiResponse(response);
    },

    async logout(): Promise<void> {
      await http.post('/auth/logout');
    },

    async me(): Promise<CurrentUserResponse> {
      const response = await http.get('/auth/me');
      return unwrapApiResponse(response);
    },
  };
}

export type AuthApi = ReturnType<typeof createAuthApi>;
