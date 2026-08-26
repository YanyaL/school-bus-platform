<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();
const error = ref<string | null>(null);

onMounted(async () => {
  try {
    await auth.completeLogout();
    await router.replace('/login');
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '统一认证退出回调失败';
  }
});
</script>

<template>
  <div class="callback">
    <el-result v-if="error" icon="warning" title="退出状态未确认" :sub-title="error">
      <template #extra
        ><el-button @click="router.replace('/login')">返回登录</el-button></template
      >
    </el-result>
    <el-result v-else icon="info" title="正在完成统一认证退出…" />
  </div>
</template>

<style scoped>
.callback {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
</style>
