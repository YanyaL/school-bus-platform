<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import AdminLayout from '@/components/AdminLayout.vue';
import { adminApiErrorMessage, createAdminApi } from '@/api/admin';
import { useAuthStore } from '@/stores/auth';
import type { Route, Trip, Vehicle } from '@/types/admin';

const auth = useAuthStore();
const api = createAdminApi(() => auth.accessToken);
const trips = ref<Trip[]>([]);
const vehicles = ref<Vehicle[]>([]);
const routes = ref<Route[]>([]);
const loading = ref(false);
const submitting = ref(false);
const dialog = ref(false);
const form = reactive({
  vehicleId: '',
  routeId: '',
  departureTime: null as Date | null,
  bookingDeadline: null as Date | null,
  price: 5,
});

const enabledVehicles = computed(() =>
  vehicles.value.filter((vehicle) => vehicle.status === 'ENABLED'),
);
const enabledRoutes = computed(() =>
  routes.value.filter((route) => route.status === 'ENABLED'),
);

async function load() {
  loading.value = true;
  try {
    [trips.value, vehicles.value, routes.value] = await Promise.all([
      api.listTrips(),
      api.listVehicles(),
      api.listRoutes(),
    ]);
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '加载班次失败'));
  } finally {
    loading.value = false;
  }
}

async function createDraft() {
  if (
    !form.vehicleId ||
    !form.routeId ||
    !form.departureTime ||
    !form.bookingDeadline
  ) {
    ElMessage.warning('请填写完整的班次信息');
    return;
  }
  if (form.bookingDeadline >= form.departureTime) {
    ElMessage.warning('预约截止时间必须早于发车时间');
    return;
  }
  submitting.value = true;
  try {
    await api.createTrip({
      vehicleId: form.vehicleId,
      routeId: form.routeId,
      departureTime: form.departureTime.toISOString(),
      bookingDeadline: form.bookingDeadline.toISOString(),
      price: form.price,
    });
    ElMessage.success('班次草稿创建成功');
    dialog.value = false;
    Object.assign(form, {
      vehicleId: '',
      routeId: '',
      departureTime: null,
      bookingDeadline: null,
      price: 5,
    });
    await load();
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '创建班次草稿失败'));
  } finally {
    submitting.value = false;
  }
}

async function publish(trip: Trip) {
  try {
    await ElMessageBox.confirm(
      `发布班次 ${trip.tripNumber} 后将开放学生预约，是否继续？`,
      '发布班次',
      { type: 'warning' },
    );
    await api.publishTrip(trip);
    ElMessage.success('班次发布成功');
    await load();
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(adminApiErrorMessage(error, '发布班次失败'));
  }
}

async function cancel(trip: Trip) {
  try {
    await ElMessageBox.confirm(
      `取消班次 ${trip.tripNumber} 可能触发订单退款流程，是否继续？`,
      '取消班次',
      { type: 'warning' },
    );
    await api.cancelTrip(trip);
    ElMessage.success('班次取消请求已提交');
    await load();
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(adminApiErrorMessage(error, '取消班次失败'));
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

onMounted(load);
</script>

<template>
  <AdminLayout>
    <el-card>
      <template #header>
        <div class="title">
          <b>班次管理</b>
          <el-button type="primary" @click="dialog = true">新建班次草稿</el-button>
        </div>
      </template>
      <el-alert
        title="班次先以草稿保存，确认车辆、路线、时间和票价后再发布。"
        type="info"
        :closable="false"
        class="hint"
      />
      <el-table v-loading="loading" :data="trips">
        <el-table-column prop="tripNumber" label="班次编号" min-width="160" />
        <el-table-column prop="vehicleId" label="车辆 ID" min-width="155" />
        <el-table-column prop="routeId" label="路线 ID" min-width="155" />
        <el-table-column label="预约截止" min-width="180">
          <template #default="scope">{{
            formatDate(scope.row.bookingDeadline)
          }}</template>
        </el-table-column>
        <el-table-column label="发车时间" min-width="180">
          <template #default="scope">{{
            formatDate(scope.row.departureTime)
          }}</template>
        </el-table-column>
        <el-table-column prop="price" label="票价" width="90" />
        <el-table-column prop="status" label="状态" min-width="155" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'DRAFT'"
              link
              type="primary"
              @click="publish(scope.row)"
            >
              发布
            </el-button>
            <el-button
              v-if="['DRAFT', 'OPEN_FOR_BOOKING', 'CLOSED'].includes(scope.row.status)"
              link
              type="danger"
              @click="cancel(scope.row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建班次草稿" width="560px">
      <el-form label-width="100px">
        <el-form-item label="车辆">
          <el-select
            v-model="form.vehicleId"
            placeholder="选择已启用车辆"
            style="width: 100%"
          >
            <el-option
              v-for="vehicle in enabledVehicles"
              :key="vehicle.vehicleId"
              :label="`${vehicle.vehicleNumber} · ${vehicle.licensePlate}`"
              :value="vehicle.vehicleId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="路线">
          <el-select
            v-model="form.routeId"
            placeholder="选择已启用路线"
            style="width: 100%"
          >
            <el-option
              v-for="route in enabledRoutes"
              :key="route.routeId"
              :label="`${route.routeCode} · ${route.departureCampus} → ${route.arrivalCampus}`"
              :value="route.routeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预约截止">
          <el-date-picker
            v-model="form.bookingDeadline"
            type="datetime"
            placeholder="选择预约截止时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="发车时间">
          <el-date-picker
            v-model="form.departureTime"
            type="datetime"
            placeholder="选择发车时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="票价">
          <el-input-number v-model="form.price" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createDraft">
          保存草稿
        </el-button>
      </template>
    </el-dialog>
  </AdminLayout>
</template>

<style scoped>
.title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.hint {
  margin-bottom: 16px;
}
</style>
