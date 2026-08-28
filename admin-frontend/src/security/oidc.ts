import {
  UserManager,
  WebStorageStateStore,
  type User,
  type UserManagerSettings,
} from 'oidc-client-ts';

export interface AdminSession {
  accessToken: string;
  expiresAt: string;
  subject: string;
  studentNumber: string;
  roles: string[];
}

interface SigninState {
  returnTo?: unknown;
}

let manager: UserManager | null = null;

export function createOidcSettings(): UserManagerSettings {
  return {
    authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8084',
    client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'school-bus-admin-web',
    redirect_uri:
      import.meta.env.VITE_OIDC_REDIRECT_URI ?? 'http://127.0.0.1:5174/auth/callback',
    post_logout_redirect_uri:
      import.meta.env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI ??
      'http://127.0.0.1:5174/auth/logout/callback',
    response_type: 'code',
    scope: 'openid profile schoolbus.read schoolbus.write',
    automaticSilentRenew: false,
    monitorSession: false,
    loadUserInfo: false,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  };
}

function userManager(): UserManager {
  manager ??= new UserManager(createOidcSettings());
  return manager;
}

export function normalizeReturnTo(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/vehicles';
  }
  return value;
}

export async function beginLogin(returnTo: string): Promise<void> {
  await userManager().signinRedirect({
    state: { returnTo: normalizeReturnTo(returnTo) },
  });
}

export async function completeLogin(): Promise<{
  session: AdminSession;
  returnTo: string;
}> {
  const user = await userManager().signinRedirectCallback();
  const state = user.state as SigninState | undefined;
  return {
    session: toAdminSession(user),
    returnTo: normalizeReturnTo(state?.returnTo),
  };
}

export async function restoreSession(): Promise<AdminSession | null> {
  const user = await userManager().getUser();
  if (!user || user.expired) {
    if (user) await userManager().removeUser();
    return null;
  }
  return toAdminSession(user);
}

export async function removeSession(): Promise<void> {
  await userManager().removeUser();
}

export async function beginLogout(): Promise<boolean> {
  const user = await userManager().getUser();
  if (!user?.id_token) {
    await userManager().removeUser();
    return false;
  }
  await userManager().signoutRedirect();
  return true;
}

export async function completeLogout(): Promise<void> {
  try {
    await userManager().signoutCallback();
  } finally {
    await userManager().removeUser();
  }
}

export function toAdminSession(user: User): AdminSession {
  if (!user.access_token || !user.expires_at) {
    throw new Error('OIDC response is missing access token metadata');
  }
  const roles = Array.isArray(user.profile.roles)
    ? user.profile.roles.filter((value): value is string => typeof value === 'string')
    : [];
  const studentNumber = user.profile.student_number;
  if (typeof studentNumber !== 'string') {
    throw new Error('OIDC response is missing student_number claim');
  }
  return {
    accessToken: user.access_token,
    expiresAt: new Date(user.expires_at * 1000).toISOString(),
    subject: user.profile.sub,
    studentNumber,
    roles,
  };
}

export function resetOidcForTests(): void {
  manager = null;
}
