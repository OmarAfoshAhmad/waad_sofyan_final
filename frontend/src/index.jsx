import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// style.scss
import 'assets/style.css';

// scroll bar
import 'simplebar-react/dist/simplebar.min.css';

// apex-chart
import 'assets/third-party/apex-chart.css';

// map
// mapbox-gl CSS removed as mapbox is not used in TBA frontend

// google-fonts
// Preload critical fonts (for example, Roboto) to reduce FOUC.
// (In a real app, you might add a <link rel="preload" as="font" href="..." crossorigin="anonymous" /> tag in your HTML (or via a plugin) for each critical font.)
// (Below is a dummy example – replace with your actual font URLs if needed.)
// <link rel="preload" as="font" href="https://fonts.gstatic.com/s/roboto/v30/KFOmCnqEu92Fr1Mu4mxK.woff2" crossorigin="anonymous" />
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/300.css';
import '@fontsource/roboto/700.css';

import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/inter/700.css';

import '@fontsource/poppins/400.css';
import '@fontsource/poppins/500.css';
import '@fontsource/poppins/600.css';
import '@fontsource/poppins/700.css';

import '@fontsource/public-sans/400.css';
import '@fontsource/public-sans/500.css';
import '@fontsource/public-sans/600.css';
import '@fontsource/public-sans/700.css';

// project imports
import App from './App';
import { ConfigProvider } from 'contexts/ConfigContext';
import reportWebVitals from './reportWebVitals';

// Production Console Zero-Noise Policy
import { applyZeroNoisePolicy } from 'utils/consoleNoiseFilter';

// Apply console filters before any other code runs
applyZeroNoisePolicy();

const container = document.getElementById('root');
const root = createRoot(container);

// Create QueryClient instance
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      refetchOnMount: false,
      retry: 1,
      staleTime: 5 * 60 * 1000, // 5 minutes
      gcTime: 15 * 60 * 1000 // keep cache longer to reduce redundant refetches
    }
  }
});

// ==============================|| MAIN - REACT DOM RENDER ||============================== //

root.render(
  <QueryClientProvider client={queryClient}>
    <ConfigProvider>
      <App />
    </ConfigProvider>
  </QueryClientProvider>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
