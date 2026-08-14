<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppEmpty from '@/components/AppEmpty.vue';
import AppLayout from '@/components/AppLayout.vue';
import AppLoading from '@/components/AppLoading.vue';
import { useAuthStore } from '@/stores/auth';
import type { BookableTrip } from '@/types/trip';
import { parseApiError, resolveUserMessage } from '@/types/api';
import { formatDateTime } from '@/utils/date';
import { formatMoney } from '@/utils/money';

const authStore = useAuthStore();
const router = useRouter();

const loading = ref(true);
const errorMessage = ref('');
const trips = ref<BookableTrip[]>([]);

async function loadTrips() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const { trips: tripsApi } = authStore.createApis();
    trips.value = await tripsApi.listBookableTrips(20);
  } catch (error) {
    const apiError = parseApiError(error);
    errorMessage.value = resolveUserMessage(apiError);
  } finally {
    loading.value = false;
  }
}

function openSeatMap(tripId: number) {
  router.push(`/trips/${tripId}/seats`);
}

onMounted(loadTrips);
</script>

<template>
  <AppLayout>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>可预约班次</span>
          <el-button :loading="loading" @click="loadTrips">刷新</el-button>
        </div>
      </template>

      <AppLoading v-if="loading" />
      <el-alert v-else-if="errorMessage" type="error" :title="errorMessage" show-icon />
      <AppEmpty v-else-if="trips.length === 0" description="当前没有可预约班次" />
      <div v-else class="trip-list">
        <el-card
          v-for="trip in trips"
          :key="trip.tripId"
          shadow="hover"
          class="trip-card"
        >
          <div class="trip-title">班次 {{ trip.tripNumber }}</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="车辆 ID">
              {{ trip.vehicleId }}
            </el-descriptions-item>
            <el-descriptions-item label="路线 ID">
              {{ trip.routeId }}
            </el-descriptions-item>
            <el-descriptions-item label="发车时间">
              {{ formatDateTime(trip.departureTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="预约截止">
              {{ formatDateTime(trip.bookingDeadline) }}
            </el-descriptions-item>
            <el-descriptions-item label="价格">
              ¥ {{ formatMoney(trip.price) }}
            </el-descriptions-item>
          </el-descriptions>
          <el-button
            type="primary"
            class="action-btn"
            @click="openSeatMap(trip.tripId)"
          >
            选座下单
          </el-button>
        </el-card>
      </div>
    </el-card>
  </AppLayout>
</template>

<style scoped>
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.trip-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.trip-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trip-title {
  font-size: 16px;
  font-weight: 600;
}

.action-btn {
  align-self: flex-start;
}
</style>
