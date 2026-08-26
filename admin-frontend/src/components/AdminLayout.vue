<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const activePath = computed(() => route.path);

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出统一认证吗？', '退出管理端', {
      type: 'warning',
    });
  } catch {
    return;
  }
  const result = await auth.logout();
  if (result === 'local-fallback') {
    ElMessage.warning('本地会话已清理，但未确认 IAM 会话已经退出');
    await router.replace('/login');
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="brand">校园班车管理端</div>
      <el-menu :default-active="activePath" router class="menu">
        <el-menu-item index="/vehicles">车辆管理</el-menu-item>
        <el-menu-item index="/routes">路线管理</el-menu-item>
        <el-menu-item index="/trips">班次管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>运营管理控制台</span>
        <div class="account">
          <el-tag type="danger">ADMIN</el-tag>
          <span>{{ auth.studentNumber }}</span>
          <el-button link type="primary" @click="logout">退出统一认证</el-button>
        </div>
      </el-header>
      <el-main><slot /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.sidebar {
  background: #17233d;
  color: white;
}
.brand {
  padding: 22px 18px;
  font-size: 18px;
  font-weight: 700;
}
.menu {
  border-right: 0;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
}
.account {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 400;
}
</style>
