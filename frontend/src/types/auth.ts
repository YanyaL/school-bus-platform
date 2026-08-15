export interface LoginRequest {
  studentNumber: string;
  password: string;
}

export interface LoginResponse {
  userId: string;
  studentNumber: string;
  roles: string[];
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface RefreshTokenResponse {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface CurrentUserResponse {
  userId: string;
  roles: string[];
}

export interface RegisterAccountRequest {
  studentNumber: string;
  password: string;
}

export interface RegisterAccountResponse {
  userId: string;
  studentNumber: string;
  status: string;
  roles: string[];
}

export interface AuthSession {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  userId: string;
  studentNumber: string;
  roles: string[];
}

export const REFRESH_TOKEN_STORAGE_KEY = 'school-bus.refreshToken';
export const STUDENT_NUMBER_STORAGE_KEY = 'school-bus.studentNumber';
