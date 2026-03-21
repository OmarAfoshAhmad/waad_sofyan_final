// أداة تعقيم HTML للوقاية من XSS
import DOMPurify from 'dompurify';

export function sanitizeHtml(dirtyHtml) {
  return DOMPurify.sanitize(dirtyHtml, { USE_PROFILES: { html: true } });
}
