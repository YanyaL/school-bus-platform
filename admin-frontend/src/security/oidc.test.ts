import type { User } from 'oidc-client-ts';
import { createOidcSettings, normalizeReturnTo, toAdminSession } from './oidc';

describe('admin OIDC configuration', () => {
  it('uses a separate public client with Authorization Code and PKCE support', () => {
    const settings = createOidcSettings();

    expect(settings.client_id).toBe('school-bus-admin-web');
    expect(settings.response_type).toBe('code');
    expect(settings.redirect_uri).toBe('http://127.0.0.1:5174/auth/callback');
    expect(settings.post_logout_redirect_uri).toBe(
      'http://127.0.0.1:5174/auth/logout/callback',
    );
    expect(settings.scope).toContain('openid');
  });

  it.each([
    [undefined, '/vehicles'],
    ['https://attacker.example', '/vehicles'],
    ['//attacker.example', '/vehicles'],
    ['/trips?status=DRAFT', '/trips?status=DRAFT'],
  ])('normalizes return target %s', (input, expected) => {
    expect(normalizeReturnTo(input)).toBe(expected);
  });

  it('maps token claims into an admin session without parsing identifiers as numbers', () => {
    const user = {
      access_token: 'access-token',
      expires_at: 2_000_000_000,
      profile: {
        sub: '9007199254740993',
        student_number: 'S4789503',
        roles: ['STUDENT', 'ADMIN'],
      },
    } as unknown as User;

    expect(toAdminSession(user)).toEqual({
      accessToken: 'access-token',
      expiresAt: new Date(2_000_000_000 * 1000).toISOString(),
      subject: '9007199254740993',
      studentNumber: 'S4789503',
      roles: ['STUDENT', 'ADMIN'],
    });
  });
});
