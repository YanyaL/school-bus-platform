import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import App from './App.vue';
import router from './router';

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(ElementPlus, { locale: zhCn });

app.mount('#app');

/**
 * Token 策略说明：
 * OIDC 会话由 oidc-client-ts 保存在 sessionStorage，不使用公共客户端 refresh token。
 * 旧 JSON 登录的 accessToken 仅在 Pinia 内存，refreshToken 仍保存在 localStorage。
 * 旧链路是当前后端返回 refresh token 条件下的迁移折中，存在 XSS 风险。
 * 正式生产环境更推荐使用 Secure、HttpOnly、SameSite Cookie 承载 refresh token。
 */
