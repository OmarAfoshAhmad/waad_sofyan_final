export const prepareSearchQuery = (value = '') => String(value).trim().replace(/\s+/g, ' ');

export const normalizeArabicSearchText = (value = '') => {
  const normalized = prepareSearchQuery(value)
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ؤ/g, 'و')
    .replace(/ئ/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/[ًٌٍَُِّْٰـ]/g, '');

  return normalized.startsWith('ال') && normalized.length > 3 ? normalized.slice(2) : normalized;
};
