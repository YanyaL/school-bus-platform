import { describe, expect, it } from 'vitest';
import { User } from 'oidc-client-ts';
import { createOidcSettings, normalizeReturnTo, toSsoSession } from './oidc';

describe('OIDC client configuration', () => {
  it('uses authorization code flow and sessionStorage', () => {
    const settings = createOidcSettings();

    expect(settings.response_type).toBe('code');
    expect(settings.client_id).toBe('school-bus-student-web');
    expect(settings.scope).toContain('openid');
    expect(settings.automaticSilentRenew).toBe(false);
    expect(settings.userStore).toBeDefined();
    expect(settings.stateStore).toBeDefined();
  });

  it('rejects external return targets', () => {
    expect(normalizeReturnTo('/bookings/BOOKING-1')).toBe('/bookings/BOOKING-1');
    expect(normalizeReturnTo('https://evil.example')).toBe('/trips');
    expect(normalizeReturnTo('//evil.example')).toBe('/trips');
    expect(normalizeReturnTo(null)).toBe('/trips');
  });

  it('maps validated OIDC user claims to the application session', () => {
    const user = new User({
      access_token: 'signed-access-token',
      token_type: 'Bearer',
      expires_at: 4_102_444_800,
      profile: {
        iss: 'https://school-bus.local',
        sub: '1000001',
        aud: 'school-bus-student-web',
        exp: 4_102_444_800,
        iat: 4_102_441_200,
        student_number: 'S4789503',
        roles: ['STUDENT'],
      },
    });

    expect(toSsoSession(user)).toEqual({
      accessToken: 'signed-access-token',
      accessTokenExpiresAt: '2100-01-01T00:00:00.000Z',
      userId: '1000001',
      studentNumber: 'S4789503',
      roles: ['STUDENT'],
    });
  });
});
