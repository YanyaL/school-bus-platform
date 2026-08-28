<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AdminLayout from '@/components/AdminLayout.vue';
import { adminApiErrorMessage, createAdminApi } from '@/api/admin';
import { useAuthStore } from '@/stores/auth';
import type { Campus, Route } from '@/types/admin';

const auth = useAuthStore();
const api = createAdminApi(() => auth.accessToken);
const routes = ref<Route[]>([]);
const loading = ref(false);
const dialog = ref(false);
const campuses: Campus[] = ['MAIN', 'EAST', 'WEST', 'NORTH'];
const form = reactive({
  routeCode: '',
  departureCampus: 'MAIN' as Campus,
  arrivalCampus: 'EAST' as Campus,
  estimatedDurationMinutes: 30,
});

async function load() {
  loading.value = true;
  try {
    routes.value = await api.listRoutes();
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '加载路线失败'));
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (form.departureCampus === form.arrivalCampus) {
    ElMessage.warning('起点和终点不能相同');
    return;
  }
  try {
    await api.createRoute(form);
    ElMessage.success('路线创建成功');
    dialog.value = false;
    await load();
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '创建路线失败'));
  }
}

async function toggle(route: Route) {
  try {
    await api.updateRouteStatus(
      route,
      route.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
    );
    await load();
  } catch (error) {
    ElMessage.error(adminApiErrorMessage(error, '更新路线状态失败'));
  }
}

onMounted(load);
</script>

<template>
  <AdminLayout>
    <el-card>
      <template #header
        ><div class="title">
          <b>路线管理</b
          ><el-button type="primary" @click="dialog = true">新增路线</el-button>
        </div></template
      >
      <el-table v-loading="loading" :data="routes">
        <el-table-column prop="routeNumber" label="路线编号" />
        <el-table-column prop="routeCode" label="路线代码" />
        <el-table-column label="方向"
          ><template #default="scope"
            >{{ scope.row.departureCampus }} → {{ scope.row.arrivalCampus }}</template
          ></el-table-column
        >
        <el-table-column prop="estimatedDurationMinutes" label="预计分钟" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作"
          ><template #default="scope"
            ><el-button link type="primary" @click="toggle(scope.row)">{{
              scope.row.status === 'ENABLED' ? '停用' : '启用'
            }}</el-button></template
          ></el-table-column
        >
      </el-table>
    </el-card>
    <el-dialog v-model="dialog" title="新增路线" width="480px">
      <el-form label-width="90px">
        <el-form-item label="路线代码"
          ><el-input v-model="form.routeCode"
        /></el-form-item>
        <el-form-item label="出发校区"
          ><el-select v-model="form.departureCampus"
            ><el-option
              v-for="campus in campuses"
              :key="campus"
              :label="campus"
              :value="campus" /></el-select
        ></el-form-item>
        <el-form-item label="到达校区"
          ><el-select v-model="form.arrivalCampus"
            ><el-option
              v-for="campus in campuses"
              :key="campus"
              :label="campus"
              :value="campus" /></el-select
        ></el-form-item>
        <el-form-item label="预计分钟"
          ><el-input-number v-model="form.estimatedDurationMinutes" :min="1"
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
