import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import router from '@/router';
import { useAuthStore } from '@/stores/auth';

describe('router auth guard', () => {
  beforeEach(async () => {
    setActivePinia(createPinia());
    localStorage.clear();
    sessionStorage.clear();
    await router.push('/');
    await router.isReady();
  });

  it('allows the public OIDC callback route', async () => {
    const store = useAuthStore();
    store.$patch({ initialized: true, accessToken: null });

    await router.push('/auth/callback?code=test&state=test');

    expect(router.currentRoute.value.name).toBe('auth-callback');
  });

  it('allows the public OIDC logout callback route', async () => {
    const store = useAuthStore();
    store.$patch({ initialized: true, accessToken: null });

    await router.push('/auth/logout/callback?state=test');

    expect(router.currentRoute.value.name).toBe('auth-logout-callback');
  });

  it('redirects unauthenticated users to login for protected routes', async () => {
    const store = useAuthStore();
    store.$patch({ initialized: true, accessToken: null });

    await router.push('/trips');
    expect(router.currentRoute.value.name).toBe('login');
    expect(router.currentRoute.value.query.redirect).toBe('/trips');
  });

  it('allows authenticated users to access trips', async () => {
    const store = useAuthStore();
    store.$patch({
      initialized: true,
      accessToken: 'token',
      userId: '1',
      studentNumber: 'S20260001',
    });

    await router.push('/trips');
    expect(router.currentRoute.value.name).toBe('trips');
  });

  it('redirects authenticated users away from login page', async () => {
    const store = useAuthStore();
    store.$patch({
      initialized: true,
      accessToken: 'token',
      userId: '1',
      studentNumber: 'S20260001',
    });

    await router.push('/trips');
    expect(router.currentRoute.value.name).toBe('trips');

    await router.push('/login');
    expect(router.currentRoute.value.name).toBe('trips');
  });
});
