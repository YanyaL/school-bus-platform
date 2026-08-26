<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const authStore = useAuthStore();
const errorMessage = ref<string | null>(null);

onMounted(async () => {
  try {
    const returnTo = await authStore.completeSsoLogin();
    await router.replace(returnTo);
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '统一认证回调处理失败';
  }
});

async function returnToLogin() {
  authStore.clearSession();
  await router.replace('/login');
}
</script>

<template>
  <div class="callback-page">
    <el-card class="callback-card">
      <el-result
        v-if="errorMessage"
        icon="error"
        title="统一认证失败"
        :sub-title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" @click="returnToLogin">返回登录页</el-button>
        </template>
      </el-result>
      <div v-else class="loading-content">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>正在校验授权码并建立登录会话…</p>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
import { Loading } from '@element-plus/icons-vue';

export default {
  components: { Loading },
};
</script>

<style scoped>
.callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: linear-gradient(180deg, #eef5ff 0%, #f5f7fa 100%);
}

.callback-card {
  width: 100%;
  max-width: 480px;
}

.loading-content {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #606266;
}
</style>
