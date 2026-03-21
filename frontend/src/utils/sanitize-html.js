// أداة تعقيم HTML للوقاية من XSS بدون اعتماد خارجي.
export function sanitizeHtml(dirtyHtml) {
  if (!dirtyHtml || typeof dirtyHtml !== 'string') return '';

  const template = document.createElement('template');
  template.innerHTML = dirtyHtml;

  // Remove dangerous elements completely.
  const blockedTags = 'script, iframe, object, embed, link, style, meta';
  template.content.querySelectorAll(blockedTags).forEach((el) => el.remove());

  // Remove event handlers and javascript: URLs from all attributes.
  template.content.querySelectorAll('*').forEach((el) => {
    [...el.attributes].forEach((attr) => {
      const name = attr.name.toLowerCase();
      const value = (attr.value || '').trim().toLowerCase();

      if (name.startsWith('on')) {
        el.removeAttribute(attr.name);
        return;
      }

      if ((name === 'href' || name === 'src' || name === 'xlink:href') && value.startsWith('javascript:')) {
        el.removeAttribute(attr.name);
      }
    });
  });

  return template.innerHTML;
}
