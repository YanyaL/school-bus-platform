<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const auth = useAuthStore();
const loading = ref(false);
const returnTo = computed(() =>
  typeof route.query.redirect === 'string' ? route.query.redirect : '/vehicles',
);

async function login() {
  loading.value = true;
  try {
    await auth.login(returnTo.value);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="card">
      <h1>校园班车管理端</h1>
      <p>使用校园统一身份认证登录。只有具有 ADMIN 角色的账号可以进入。</p>
      <el-alert
        title="管理员权限由运维初始化，不提供公开管理员注册接口"
        type="info"
        :closable="false"
        show-icon
      />
      <el-button type="primary" size="large" :loading="loading" @click="login">
        使用校园统一身份认证
      </el-button>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #17233d, #315b96);
}
.card {
  width: min(430px, calc(100vw - 32px));
}
.card h1 {
  margin-top: 0;
}
.card p {
  color: #606266;
  line-height: 1.7;
}
.card .el-button {
  width: 100%;
  margin-top: 22px;
}
</style>
