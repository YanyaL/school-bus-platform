import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/vehicles' },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('@/views/AuthCallbackView.vue'),
    },
    {
      path: '/auth/logout/callback',
      name: 'auth-logout-callback',
      component: () => import('@/views/AuthLogoutCallbackView.vue'),
    },
    {
      path: '/vehicles',
      name: 'vehicles',
      component: () => import('@/views/VehiclesView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/routes',
      name: 'routes',
      component: () => import('@/views/RoutesView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/trips',
      name: 'trips',
      component: () => import('@/views/TripsView.vue'),
      meta: { requiresAdmin: true },
    },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.initialized) await auth.initialize();
  if (to.meta.requiresAdmin && (!auth.isAuthenticated || !auth.isAdmin)) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.meta.guestOnly && auth.isAuthenticated) return { name: 'vehicles' };
  return true;
});

export default router;
