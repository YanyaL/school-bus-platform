<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { useAuthStore } from '@/stores/auth';

const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();

const activePath = computed(() => route.path);

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    });
    await authStore.logout();
    await router.push('/login');
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <el-container class="app-layout">
    <el-header class="app-header">
      <div class="brand" @click="router.push('/trips')">校园班车预约</div>
      <el-menu
        mode="horizontal"
        :default-active="activePath"
        router
        class="nav-menu"
        :ellipsis="false"
      >
        <el-menu-item index="/trips">班次列表</el-menu-item>
        <el-menu-item index="/bookings">我的订单</el-menu-item>
      </el-menu>
      <div class="user-area">
        <span class="user-name">{{ authStore.displayName }}</span>
        <el-button link type="primary" @click="handleLogout">
          {{ authStore.authMode === 'sso' ? '退出当前应用' : '退出登录' }}
        </el-button>
      </div>
    </el-header>
    <el-main class="app-main">
      <slot />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 16px;
}

.brand {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  cursor: pointer;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.user-name {
  color: #606266;
  font-size: 14px;
}

.app-main {
  max-width: 1080px;
  margin: 0 auto;
  width: 100%;
  padding: 16px;
}

@media (max-width: 768px) {
  .app-header {
    flex-wrap: wrap;
    height: auto;
    padding: 12px 16px;
  }

  .nav-menu {
    order: 3;
    width: 100%;
  }

  .user-area {
    margin-left: auto;
  }
}
</style>
