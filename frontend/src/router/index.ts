import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: () => {
        const authStore = useAuthStore();
        return authStore.isAuthenticated ? '/trips' : '/login';
      },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
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
      path: '/trips',
      name: 'trips',
      component: () => import('@/views/TripListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/trips/:tripNumber/seats',
      name: 'trip-seats',
      component: () => import('@/views/TripSeatView.vue'),
      meta: { requiresAuth: true },
      props: true,
    },
    {
      path: '/bookings',
      name: 'bookings',
      component: () => import('@/views/BookingListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/bookings/:bookingNumber',
      name: 'booking-detail',
      component: () => import('@/views/BookingDetailView.vue'),
      meta: { requiresAuth: true },
      props: true,
    },
  ],
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();

  if (!authStore.initialized) {
    await authStore.initializeSession();
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    };
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: 'trips' };
  }

  return true;
});

export default router;
