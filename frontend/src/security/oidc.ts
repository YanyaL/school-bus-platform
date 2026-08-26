import {
  UserManager,
  WebStorageStateStore,
  type User,
  type UserManagerSettings,
} from 'oidc-client-ts';

export interface SsoSession {
  accessToken: string;
  accessTokenExpiresAt: string;
  userId: string;
  studentNumber: string;
  roles: string[];
}

export interface SsoCallbackResult {
  session: SsoSession;
  returnTo: string;
}

interface SigninState {
  returnTo?: unknown;
}

let userManager: UserManager | null = null;

export function createOidcSettings(): UserManagerSettings {
  return {
    authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8084',
    client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'school-bus-student-web',
    redirect_uri:
      import.meta.env.VITE_OIDC_REDIRECT_URI ?? 'http://127.0.0.1:5173/auth/callback',
    post_logout_redirect_uri:
      import.meta.env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI ??
      'http://127.0.0.1:5173/login',
    response_type: 'code',
    scope: 'openid profile schoolbus.read schoolbus.write',
    automaticSilentRenew: false,
    monitorSession: false,
    loadUserInfo: false,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  };
}

function getUserManager(): UserManager {
  if (!userManager) {
    userManager = new UserManager(createOidcSettings());
  }
  return userManager;
}

export function normalizeReturnTo(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/trips';
  }
  return value;
}

export async function beginSsoLogin(returnTo: string): Promise<void> {
  await getUserManager().signinRedirect({
    state: { returnTo: normalizeReturnTo(returnTo) },
  });
}

export async function completeSsoLogin(): Promise<SsoCallbackResult> {
  const user = await getUserManager().signinRedirectCallback();
  const state = user.state as SigninState | undefined;
  return {
    session: toSsoSession(user),
    returnTo: normalizeReturnTo(state?.returnTo),
  };
}

export async function restoreSsoSession(): Promise<SsoSession | null> {
  const user = await getUserManager().getUser();
  if (!user || user.expired) {
    if (user) {
      await getUserManager().removeUser();
    }
    return null;
  }
  return toSsoSession(user);
}

export async function removeSsoSession(): Promise<void> {
  await getUserManager().removeUser();
}

export function toSsoSession(user: User): SsoSession {
  if (!user.access_token || !user.expires_at) {
    throw new Error('OIDC response is missing access token metadata');
  }

  const studentNumber = stringClaim(user.profile.student_number);
  if (!studentNumber) {
    throw new Error('OIDC response is missing student_number claim');
  }

  return {
    accessToken: user.access_token,
    accessTokenExpiresAt: new Date(user.expires_at * 1000).toISOString(),
    userId: user.profile.sub,
    studentNumber,
    roles: stringListClaim(user.profile.roles),
  };
}

function stringClaim(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null;
}

function stringListClaim(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((role): role is string => typeof role === 'string').sort();
  }
  if (typeof value === 'string' && value.length > 0) {
    return [value];
  }
  return [];
}

export function resetOidcUserManagerForTests(): void {
  userManager = null;
}
