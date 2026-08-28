import pluginVue from 'eslint-plugin-vue';
import vueTs from '@vue/eslint-config-typescript';
import skipFormatting from '@vue/eslint-config-prettier';

export default [
  { files: ['**/*.{ts,mts,tsx,vue}'] },
  { ignores: ['dist/**', 'node_modules/**'] },
  ...pluginVue.configs['flat/recommended'],
  ...vueTs(),
  skipFormatting,
  {
    rules: {
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
];
