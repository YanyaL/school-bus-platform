<template>
  <div class="app-loading">
    <el-icon class="spinning"><Loading /></el-icon>
    <span>{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue';

withDefaults(
  defineProps<{
    text?: string;
  }>(),
  {
    text: '加载中…',
  },
);
</script>

<style scoped>
.app-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 16px;
  color: #909399;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
