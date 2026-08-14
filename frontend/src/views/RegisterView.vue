<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { createAuthApi } from '@/api/auth';
import { createRefreshClient } from '@/api/http';
import { parseApiError, resolveUserMessage } from '@/types/api';

const router = useRouter();
const authApi = createAuthApi(createRefreshClient());

const form = reactive({
  studentNumber: '',
  password: '',
  confirmPassword: '',
});

const loading = ref(false);

function validateForm(): boolean {
  if (!form.studentNumber.trim()) {
    ElMessage.warning('请输入学号');
    return false;
  }
  if (form.password.length < 8) {
    ElMessage.warning('密码至少 8 位');
    return false;
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致');
    return false;
  }
  return true;
}

async function handleSubmit() {
  if (!validateForm()) {
    return;
  }

  loading.value = true;
  try {
    await authApi.register({
      studentNumber: form.studentNumber.trim(),
      password: form.password,
    });
    ElMessage.success('注册成功，请登录');
    await router.push('/login');
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
        <div class="card-title">学生注册</div>
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
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          class="submit-btn"
          :loading="loading"
          @click="handleSubmit"
        >
          注册
        </el-button>
      </el-form>
      <div class="footer-link">
        已有账号？
        <el-button link type="primary" @click="router.push('/login')">去登录</el-button>
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
