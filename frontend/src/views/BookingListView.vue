<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppEmpty from '@/components/AppEmpty.vue';
import AppLayout from '@/components/AppLayout.vue';
import AppLoading from '@/components/AppLoading.vue';
import BookingStatusTag from '@/components/BookingStatusTag.vue';
import { useAuthStore } from '@/stores/auth';
import type { BookingStatus, BookingSummary } from '@/types/booking';
import { parseApiError, resolveUserMessage } from '@/types/api';
import { formatDateTime } from '@/utils/date';
import { formatMoney } from '@/utils/money';

const authStore = useAuthStore();
const router = useRouter();

const loading = ref(true);
const errorMessage = ref('');
const bookings = ref<BookingSummary[]>([]);
const page = ref(0);
const size = ref(10);
const totalElements = ref(0);
const statusFilter = ref<BookingStatus | ''>('');

async function loadBookings() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const { bookings: bookingsApi } = authStore.createApis();
    const response = await bookingsApi.listMyBookings({
      page: page.value,
      size: size.value,
      sort: 'createdAt,desc',
      status: statusFilter.value || undefined,
    });
    bookings.value = response.items;
    totalElements.value = response.totalElements;
  } catch (error) {
    const apiError = parseApiError(error);
    errorMessage.value = resolveUserMessage(apiError);
  } finally {
    loading.value = false;
  }
}

function handlePageChange(nextPage: number) {
  page.value = nextPage - 1;
  loadBookings();
}

function handleFilterChange() {
  page.value = 0;
  loadBookings();
}

function openDetail(bookingNumber: string) {
  router.push(`/bookings/${bookingNumber}`);
}

function handleRowClick(row: BookingSummary) {
  openDetail(row.bookingNumber);
}

onMounted(loadBookings);
</script>

<template>
  <AppLayout>
    <el-card>
      <template #header>
        <div class="header-row">
          <span>我的订单</span>
          <div class="filters">
            <el-select
              v-model="statusFilter"
              placeholder="全部状态"
              clearable
              style="width: 160px"
              @change="handleFilterChange"
            >
              <el-option label="待支付" value="PENDING_PAYMENT" />
              <el-option label="已支付" value="PAID" />
              <el-option label="退款中" value="REFUND_PENDING" />
              <el-option label="已取消" value="CANCELLED" />
              <el-option label="已退款" value="REFUNDED" />
            </el-select>
            <el-button :loading="loading" @click="loadBookings">刷新</el-button>
          </div>
        </div>
      </template>

      <AppLoading v-if="loading" />
      <el-alert v-else-if="errorMessage" type="error" :title="errorMessage" show-icon />
      <AppEmpty v-else-if="bookings.length === 0" description="暂无订单" />
      <template v-else>
        <el-table :data="bookings" stripe @row-click="handleRowClick">
          <el-table-column prop="bookingNumber" label="订单号" min-width="220" />
          <el-table-column prop="tripId" label="班次 ID" width="100" />
          <el-table-column prop="seatNumber" label="座位" width="100" />
          <el-table-column label="金额" width="120">
            <template #default="{ row }">¥ {{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <BookingStatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page + 1"
            :page-size="size"
            :total="totalElements"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </el-card>
  </AppLayout>
</template>

<style scoped>
.header-row,
.filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
