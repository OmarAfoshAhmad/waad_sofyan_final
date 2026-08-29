import { useEffect } from 'react';
import { useSnackbar } from 'notistack';

/**
 * The last link in the tracking chain.
 *
 * LogMdcFilter stamps a traceId on every request, logback prints it on every
 * line as [%X{traceId}], and GlobalExceptionHandler returns it as trackingId
 * on the error body. None of that helped anyone: the user saw a sentence, and
 * whoever they reported it to had no way to find the line it came from.
 *
 * The id is shown quietly and shortened -- the first segment of the UUID is
 * enough to find a line in a day's logs, and a full one in a toast is noise
 * nobody reads. It is logged in full beside it for anyone with the console
 * open.
 */
export default function GlobalApiErrorToaster() {
  const { enqueueSnackbar } = useSnackbar();

  useEffect(() => {
    const handler = (event) => {
      const detail = event?.detail || {};
      if (detail.status === 401 || detail.statusCode === 401) return;

      const message = detail.message || 'حدث خطأ غير متوقع';
      const shortId = detail.trackingId ? String(detail.trackingId).split('-')[0] : null;

      enqueueSnackbar(shortId ? `${message} (مرجع: ${shortId})` : message, {
        variant: 'error',
        autoHideDuration: 5000
      });

      if (detail.trackingId) {
        console.warn('[API Error] trackingId:', detail.trackingId, detail.code || '');
      }
      if (detail.details) {
        console.warn('[API Error Details]', detail.details);
      }
    };

    window.addEventListener('api:error', handler);
    return () => window.removeEventListener('api:error', handler);
  }, [enqueueSnackbar]);

  return null;
}
