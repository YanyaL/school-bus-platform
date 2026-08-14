<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppEmpty from '@/components/AppEmpty.vue';
import AppLayout from '@/components/AppLayout.vue';
import AppLoading from '@/components/AppLoading.vue';
import SeatGrid from '@/components/SeatGrid.vue';
import { useAuthStore } from '@/stores/auth';
import type { BookableTrip, TripSeatMap } from '@/types/trip';
import { parseApiError, resolveUserMessage } from '@/types/api';
import { IdempotencySession } from '@/utils/idempotency';
import { formatDateTime } from '@/utils/date';
import { formatMoney } from '@/utils/money';

const props = defineProps<{
  tripId: string;
}>();

const authStore = useAuthStore();
const router = useRouter();
const idempotencySession = new IdempotencySession();

const loading = ref(true);
const submitting = ref(false);
const errorMessage = ref('');
const seatMap = ref<TripSeatMap | null>(null);
const trip = ref<BookableTrip | null>(null);
const selectedSeat = ref<string | null>(null);

const numericTripId = computed(() => Number(props.tripId));

async function loadPageData() {
  loading.value = true;
  errorMessage.value = '';
  selectedSeat.value = null;
  idempotencySession.reset();

  try {
    const { trips } = authStore.createApis();
    const [tripsList, seats] = await Promise.all([
      trips.listBookableTrips(100),
      trips.getTripSeats(numericTripId.value),
    ]);
    seatMap.value = seats;
    trip.value = tripsList.find((item) => item.tripId === numericTripId.value) ?? null;
  } catch (error) {
    const apiError = parseApiError(error);
    errorMessage.value = resolveUserMessage(apiError);
  } finally {
    loading.value = false;
  }
}

async function submitBooking() {
  if (!selectedSeat.value || !trip.value) {
    ElMessage.warning('请选择可用座位');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认预约班次 ${trip.value.tripNumber}，座位 ${selectedSeat.value}，价格 ¥ ${formatMoney(trip.value.price)}？`,
      '确认下单',
      {
        confirmButtonText: '确认下单',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  submitting.value = true;
  const idempotencyKey = idempotencySession.begin();

  try {
    const { bookings } = authStore.createApis();
    const result = await bookings.createBooking(
      {
        tripId: numericTripId.value,
        seatNumber: selectedSeat.value,
      },
      idempotencyKey,
    );

    if (result.idempotencyReplayed) {
      ElMessage.info('检测到幂等重放，已返回同一订单结果');
    } else {
      ElMessage.success('订单创建成功');
    }

    idempotencySession.reset();
    await router.push(`/bookings/${result.data.bookingNumber}`);
  } catch (error) {
    const apiError = parseApiError(error);
    ElMessage.error(resolveUserMessage(apiError));
  } finally {
    submitting.value = false;
  }
}

onMounted(loadPageData);
</script>

<template>
  <AppLayout>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>座位选择</span>
          <el-button @click="router.push('/trips')">返回班次列表</el-button>
        </div>
      </template>

      <AppLoading v-if="loading" />
      <el-alert v-else-if="errorMessage" type="error" :title="errorMessage" show-icon />
      <template v-else-if="seatMap">
        <el-descriptions v-if="trip" :column="1" border class="trip-info">
          <el-descriptions-item label="班次编号">
            {{ trip.tripNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="发车时间">
            {{ formatDateTime(trip.departureTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="预约截止">
            {{ formatDateTime(seatMap.bookingDeadline) }}
          </el-descriptions-item>
          <el-descriptions-item label="价格">
            ¥ {{ formatMoney(trip.price) }}
          </el-descriptions-item>
        </el-descriptions>

        <AppEmpty v-if="seatMap.seats.length === 0" description="暂无座位数据" />
        <SeatGrid v-else v-model="selectedSeat" :seats="seatMap.seats" />

        <div class="actions">
          <el-button
            type="primary"
            :disabled="!selectedSeat"
            :loading="submitting"
            @click="submitBooking"
          >
            创建订单
          </el-button>
        </div>
      </template>
    </el-card>
  </AppLayout>
</template>

<style scoped>
.header-row,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.trip-info {
  margin-bottom: 16px;
}

.actions {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
