<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AdminLayout from '@/components/AdminLayout.vue';
import { adminApiErrorMessage, createAdminApi } from '@/api/admin';
import { useAuthStore } from '@/stores/auth';
import type { Vehicle } from '@/types/admin';

const auth = useAuthStore();
const api = createAdminApi(() => auth.accessToken);
const vehicles = ref<Vehicle[]>([]);
const loading = ref(false);
const dialog = ref(false);
const form = reactive({ licensePlate: '', seatCount: 45 });

async function load() {
  loading.value = true;
  try {
    vehicles.value = await api.listVehicles();
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '加载车辆失败'));
  } finally {
    loading.value = false;
  }
}

async function create() {
  try {
    await api.createVehicle(form);
    ElMessage.success('车辆创建成功');
    dialog.value = false;
    form.licensePlate = '';
    await load();
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '创建车辆失败'));
  }
}

async function toggle(vehicle: Vehicle) {
  try {
    await api.updateVehicleStatus(
      vehicle,
      vehicle.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
    );
    await load();
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '更新车辆状态失败'));
  }
}

onMounted(load);
</script>

<template>
  <AdminLayout>
    <el-card>
      <template #header>
        <div class="title">
          <b>车辆管理</b
          ><el-button type="primary" @click="dialog = true">新增车辆</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="vehicles">
        <el-table-column prop="vehicleNumber" label="车辆编号" />
        <el-table-column prop="licensePlate" label="车牌" />
        <el-table-column prop="seatCount" label="座位数" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作">
          <template #default="scope">
            <el-button link type="primary" @click="toggle(scope.row)">
              {{ scope.row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialog" title="新增车辆" width="420px">
      <el-form label-width="80px">
        <el-form-item label="车牌"
          ><el-input v-model="form.licensePlate"
        /></el-form-item>
        <el-form-item label="座位数"
          ><el-input-number v-model="form.seatCount" :min="1" :max="120"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button type="primary" @click="create">保存</el-button></template
      >
    </el-dialog>
  </AdminLayout>
</template>

<style scoped>
.title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
