<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { parseApiError, resolveUserMessage } from '@/types/api';

const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();

const form = reactive({
  studentNumber: '',
  password: '',
});

const loading = ref(false);

async function handleSubmit() {
  if (!form.studentNumber.trim() || !form.password) {
    ElMessage.warning('请输入学号和密码');
    return;
  }

  loading.value = true;
  try {
    await authStore.login({
      studentNumber: form.studentNumber.trim(),
      password: form.password,
    });
    const redirect =
      typeof route.query.redirect === 'string' ? route.query.redirect : '/trips';
    await router.replace(redirect);
  } catch (error) {
    const apiError = parseApiError(error);
    ElMessage.error(resolveUserMessage(apiError));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <template #header>
        <div class="card-title">学生登录</div>
      </template>
      <el-form label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="学号">
          <el-input v-model="form.studentNumber" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          class="submit-btn"
          :loading="loading"
          @click="handleSubmit"
        >
          登录
        </el-button>
      </el-form>
      <div class="footer-link">
        还没有账号？
        <el-button link type="primary" @click="router.push('/register')"
          >去注册</el-button
        >
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: linear-gradient(180deg, #eef5ff 0%, #f5f7fa 100%);
}

.auth-card {
  width: 100%;
  max-width: 420px;
}

.card-title {
  font-size: 20px;
  font-weight: 600;
}

.submit-btn {
  width: 100%;
}

.footer-link {
  margin-top: 16px;
  text-align: center;
  color: #606266;
}
</style>
