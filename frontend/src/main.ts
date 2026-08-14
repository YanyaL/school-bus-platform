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
 * accessToken 仅保存在 Pinia 内存；refreshToken 保存在 localStorage。
 * 这是当前后端在 JSON 响应体返回 refresh token 条件下的工程折中，存在 XSS 风险。
 * 正式生产环境更推荐使用 Secure、HttpOnly、SameSite Cookie 承载 refresh token。
 */
