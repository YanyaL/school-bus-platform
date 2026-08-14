import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiResponse } from '@/types/api';
import { parseApiError } from '@/types/api';

export const API_BASE_URL = '/api/v1';

type AccessTokenProvider = () => string | null;
type RefreshHandler = () => Promise<string>;
type UnauthorizedHandler = () => void;

interface AuthInterceptorOptions {
  getAccessToken: AccessTokenProvider;
  refreshAccessToken: RefreshHandler;
  onUnauthorized: UnauthorizedHandler;
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

let sharedRefreshPromise: Promise<string> | null = null;

export function createRefreshClient(): AxiosInstance {
  return axios.create({
    baseURL: API_BASE_URL,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

export function createHttpClient(options: AuthInterceptorOptions): AxiosInstance {
  const client = axios.create({
    baseURL: API_BASE_URL,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  client.interceptors.request.use((config) => {
    const token = options.getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalConfig = error.config as RetriableRequestConfig | undefined;
      const status = error.response?.status;

      if (
        status !== 401 ||
        !originalConfig ||
        originalConfig._retry ||
        originalConfig.url?.includes('/auth/refresh')
      ) {
        return Promise.reject(parseApiError(error));
      }

      originalConfig._retry = true;

      try {
        if (!sharedRefreshPromise) {
          sharedRefreshPromise = options.refreshAccessToken().finally(() => {
            sharedRefreshPromise = null;
          });
        }
        const newToken = await sharedRefreshPromise;
        originalConfig.headers.Authorization = `Bearer ${newToken}`;
        return client.request(originalConfig);
      } catch (refreshError) {
        options.onUnauthorized();
        return Promise.reject(parseApiError(refreshError));
      }
    },
  );

  return client;
}

export function unwrapApiResponse<T>(response: AxiosResponse<ApiResponse<T>>): T {
  return response.data.data;
}

export function resetSharedRefreshPromiseForTests(): void {
  sharedRefreshPromise = null;
}
