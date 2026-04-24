import { useEffect } from 'react';
import { useSnackbar } from 'notistack';

export default function GlobalApiErrorToaster() {
  const { enqueueSnackbar } = useSnackbar();

  useEffect(() => {
    const handler = (event) => {
      const detail = event?.detail || {};
      const message = detail.message || 'حدث خطأ غير متوقع';

      enqueueSnackbar(message, {
        variant: 'error',
        autoHideDuration: 5000
      });

      if (detail.details) {
        console.warn('[API Error Details]', detail.details);
      }
    };

    window.addEventListener('api:error', handler);
    return () => window.removeEventListener('api:error', handler);
  }, [enqueueSnackbar]);

  return null;
}
