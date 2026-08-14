<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppLayout from '@/components/AppLayout.vue';
import AppLoading from '@/components/AppLoading.vue';
import BookingStatusTag from '@/components/BookingStatusTag.vue';
import { useAuthStore } from '@/stores/auth';
import type { BookingDetail } from '@/types/booking';
import { cancellationReasonLabel } from '@/types/booking';
import { parseApiError, resolveUserMessage } from '@/types/api';
import { formatDateTime, formatRemainingTime } from '@/utils/date';
import { formatMoney } from '@/utils/money';

const props = defineProps<{
  bookingNumber: string;
}>();

const authStore = useAuthStore();
const router = useRouter();

const loading = ref(true);
const refreshing = ref(false);
const cancelling = ref(false);
const errorMessage = ref('');
const booking = ref<BookingDetail | null>(null);
const remainingText = ref('');
let timer: number | undefined;

const canCancel = computed(() => booking.value?.status === 'PENDING_PAYMENT');

function updateRemainingTime() {
  if (!booking.value || booking.value.status !== 'PENDING_PAYMENT') {
    remainingText.value = '';
    return;
  }
  remainingText.value = formatRemainingTime(booking.value.expiresAt);
}

async function loadDetail(showFullLoading = true) {
  if (showFullLoading) {
    loading.value = true;
  } else {
    refreshing.value = true;
  }
  errorMessage.value = '';

  try {
    const { bookings } = authStore.createApis();
    booking.value = await bookings.getBookingDetail(props.bookingNumber);
    updateRemainingTime();
  } catch (error) {
    const apiError = parseApiError(error);
    errorMessage.value = resolveUserMessage(apiError);
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function handleCancel() {
  if (!booking.value) {
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${booking.value.bookingNumber} 吗？`,
      '取消订单',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '返回',
      },
    );
  } catch {
    return;
  }

  cancelling.value = true;
  try {
    const { bookings } = authStore.createApis();
    await bookings.cancelBooking(props.bookingNumber);
    ElMessage.success('订单已取消');
    await loadDetail(false);
  } catch (error) {
    const apiError = parseApiError(error);
    ElMessage.error(resolveUserMessage(apiError));
  } finally {
    cancelling.value = false;
  }
}

onMounted(() => {
  loadDetail();
  timer = window.setInterval(updateRemainingTime, 1000);
});

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer);
  }
});
</script>

<template>
  <AppLayout>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>订单详情</span>
          <div class="actions">
            <el-button @click="router.push('/bookings')">返回列表</el-button>
            <el-button :loading="refreshing" @click="loadDetail(false)"
              >刷新状态</el-button
            >
          </div>
        </div>
      </template>

      <AppLoading v-if="loading" />
      <el-alert v-else-if="errorMessage" type="error" :title="errorMessage" show-icon />
      <template v-else-if="booking">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">
            {{ booking.bookingNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="班次 ID">
            {{ booking.tripId }}
          </el-descriptions-item>
          <el-descriptions-item label="座位">
            {{ booking.seatNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="金额">
            ¥ {{ formatMoney(booking.amount) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <BookingStatusTag :status="booking.status" />
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(booking.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="支付截止时间">
            {{ formatDateTime(booking.expiresAt) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="booking.status === 'PENDING_PAYMENT'"
            label="支付剩余时间"
          >
            {{ remainingText }}
          </el-descriptions-item>
          <el-descriptions-item v-if="booking.paidAt" label="支付时间">
            {{ formatDateTime(booking.paidAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="booking.cancelledAt" label="取消时间">
            {{ formatDateTime(booking.cancelledAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="booking.cancelReason" label="取消原因">
            {{ cancellationReasonLabel(booking.cancelReason) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          class="payment-note"
          type="info"
          :closable="false"
          title="支付说明"
          description="浏览器端不保存支付密钥，无法直接模拟支付回调。本地演示支付请使用后端 swagger-e2e-demo.ps1 或支付回调接口完成。"
        />

        <div v-if="canCancel" class="footer-actions">
          <el-button type="danger" :loading="cancelling" @click="handleCancel">
            取消订单
          </el-button>
        </div>
      </template>
    </el-card>
  </AppLayout>
</template>

<style scoped>
.header-row,
.actions,
.footer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-row {
  justify-content: space-between;
  flex-wrap: wrap;
}

.payment-note {
  margin-top: 16px;
}

.footer-actions {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
