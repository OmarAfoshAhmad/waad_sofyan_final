import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import jsconfigPaths from 'vite-jsconfig-paths';
import path from 'path';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const API_URL = env.VITE_APP_BASE_NAME || '/';
  const API_BASE_URL = env.VITE_API_URL || 'http://localhost:8081/api';
  const PORT = 3000;

  return {
    base: API_URL,
    // Force host to localhost and disable auto open to avoid external preview links (Codespaces previews)
    server: {
      open: false,
      port: PORT,
      host: true,
      proxy: {
        '/api': {
          target: 'http://localhost:8081',
          changeOrigin: true,
          secure: false
        }
      }
    },
    preview: {
      open: false,
      host: 'localhost'
    },
    define: {
      global: 'window' // Only if you need it for legacy packages
    },
    resolve: {
      alias: {
        '@ant-design/icons': path.resolve(__dirname, 'node_modules/@ant-design/icons'),
        'assets': path.resolve(__dirname, 'src/assets'),
        'components': path.resolve(__dirname, 'src/components'),
        'contexts': path.resolve(__dirname, 'src/contexts'),
        'utils': path.resolve(__dirname, 'src/utils'),
        'services': path.resolve(__dirname, 'src/services'),
        'pages': path.resolve(__dirname, 'src/pages'),
        'hooks': path.resolve(__dirname, 'src/hooks'),
        'api': path.resolve(__dirname, 'src/api'),
        'config': path.resolve(__dirname, 'src/config'),
        'store': path.resolve(__dirname, 'src/store'),
        'themes': path.resolve(__dirname, 'src/themes'),
        'layout': path.resolve(__dirname, 'src/layout'),
        'routes': path.resolve(__dirname, 'src/routes'),
        'constants': path.resolve(__dirname, 'src/constants'),
        'sections': path.resolve(__dirname, 'src/sections'),
        'data': path.resolve(__dirname, 'src/data'),
        'metrics': path.resolve(__dirname, 'src/metrics'),
        'schemas': path.resolve(__dirname, 'src/schemas'),
        'theme': path.resolve(__dirname, 'src/theme'),
        'locales': path.resolve(__dirname, 'src/locales'),
        'menu-items': path.resolve(__dirname, 'src/menu-items')
      }
    },
    plugins: [react(), jsconfigPaths()],

    optimizeDeps: {
      include: ['@mui/material/Tooltip', 'react', 'react-dom', 'react-router-dom']
    },
    esbuild: {
      drop: mode === 'production' ? ['console', 'debugger'] : []
    },
    build: {
      chunkSizeWarningLimit: 1000, // Raise warning limit to 1000kb
      rollupOptions: {
        onwarn(warning, warn) {
          if (warning.code === 'EVAL' && typeof warning.id === 'string' && warning.id.includes('exceljs.min.js')) {
            return;
          }
          warn(warning);
        },
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              const is = (pkg) => id.includes(`/${pkg}/`) || id.includes(`\\${pkg}\\`);

              // ExcelJS is the only truly standalone chunk (large, no React dependency)
              if (is('exceljs')) {
                return 'excel';
              }
              // Large standalone libs that can be split safely from the core vendor chunk
              if (is('xlsx')) {
                return 'xlsx';
              }
              if (is('pdfjs-dist')) {
                return 'pdfjs';
              }
              if (is('react-pdf')) {
                return 'react-pdf';
              }
              if (is('recharts') || is('apexcharts') || is('react-apexcharts') || is('chart.js') || is('react-chartjs-2')) {
                return 'charts';
              }
              if (is('framer-motion')) {
                return 'motion';
              }
              if (is('lodash-es')) {
                return 'lodash';
              }
              if (
                id.includes('/@mui/x-data-grid/') ||
                id.includes('\\@mui\\x-data-grid\\') ||
                id.includes('/@mui/x-date-pickers/') ||
                id.includes('\\@mui\\x-date-pickers\\') ||
                id.includes('/@mui/x-charts/') ||
                id.includes('\\@mui\\x-charts\\')
              ) {
                return 'mui-x';
              }
              if (is('material-react-table')) {
                return 'mrt';
              }
              // EVERYTHING ELSE from node_modules goes into vendor.
              // Keep React + MUI ecosystem in vendor to avoid brittle init ordering.
              return 'vendor';
            }
          }
        }
      }
    }
  };
});