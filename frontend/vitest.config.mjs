import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      assets: path.resolve(__dirname, 'src/assets'),
      components: path.resolve(__dirname, 'src/components'),
      contexts: path.resolve(__dirname, 'src/contexts'),
      utils: path.resolve(__dirname, 'src/utils'),
      services: path.resolve(__dirname, 'src/services'),
      pages: path.resolve(__dirname, 'src/pages'),
      hooks: path.resolve(__dirname, 'src/hooks'),
      api: path.resolve(__dirname, 'src/api'),
      config: path.resolve(__dirname, 'src/config'),
      store: path.resolve(__dirname, 'src/store'),
      themes: path.resolve(__dirname, 'src/themes'),
      layout: path.resolve(__dirname, 'src/layout'),
      routes: path.resolve(__dirname, 'src/routes'),
      constants: path.resolve(__dirname, 'src/constants'),
      sections: path.resolve(__dirname, 'src/sections'),
      data: path.resolve(__dirname, 'src/data'),
      metrics: path.resolve(__dirname, 'src/metrics'),
      schemas: path.resolve(__dirname, 'src/schemas'),
      theme: path.resolve(__dirname, 'src/theme'),
      locales: path.resolve(__dirname, 'src/locales'),
      'menu-items': path.resolve(__dirname, 'src/menu-items')
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.js'],
    css: true
  }
});
