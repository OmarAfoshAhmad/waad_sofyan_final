const normalizeText = (value) => (value == null ? '' : String(value).trim());
const numericPattern = /^[\d\s.,]+$/;
const letterPattern = /[A-Za-z\u0600-\u06FF]/;
const headerOnlyWords = [
  'service_name', 'اسم الخدمة', 'اسم الخدمة عربي', 'اسم الخدمة إنجليزي',
  'البيان بالعربي', 'البيان باللاتيني', 'الخدمة', 'الخدمه',
  'medicalserviceslistemultinature', 'contract_price', 'service_code',
  'medical_category_code', 'medical_category_name', 'السعر', 'الكود',
  'الرمز', 'التصنيف', 'التصنيف التأميني', 'الخدمات'
];

const parseNumber = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const text = normalizeText(value).replace(/,/g, '');
  if (!text) return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
};

export const parsePriceRange = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return { min: value, max: value, label: value };
  const raw = normalizeText(value);
  if (!raw) return { min: null, max: null, label: '' };
  const normalized = raw.replace(/,/g, '').replace(/[–—−]/g, '-')
    .replace(/\b(to)\b/gi, '-').replace(/إلى|الى|لغاية|حتى|من/gi, '-')
    .replace(/[^\d.\-\s]/g, ' ').replace(/\s+/g, ' ').trim();
  const numbers = normalized.split(/[\s-]+/).map(Number)
    .filter((number) => Number.isFinite(number) && number > 0);
  if (!numbers.length) {
    const single = parseNumber(raw);
    return single == null ? { min: null, max: null, label: raw } : { min: single, max: single, label: single };
  }
  const min = Math.min(...numbers);
  const max = Math.max(...numbers);
  return { min, max, label: min === max ? min : `${min}-${max}` };
};

const isLikelyServiceName = (value) => {
  const text = normalizeText(value);
  if (text.length < 3 || text.length > 220 || !letterPattern.test(text) || numericPattern.test(text)) return false;
  const lower = text.toLowerCase();
  if (headerOnlyWords.some((word) => lower === word.toLowerCase() || lower.includes(`${word.toLowerCase()}:`))) return false;
  return !lower.endsWith('.xlsx') && !lower.endsWith('.xls') && !(lower.includes('خدمات ') && lower.includes('xlsx'));
};

const splitEmbeddedServiceCode = (serviceName, explicitCode) => {
  if (explicitCode) return { serviceName, serviceCode: explicitCode };
  const match = serviceName.match(/^([A-Za-z]{1,12}(?:[-/]?[A-Za-z0-9]+)+)\s+(.+)$/u);
  return match ? { serviceCode: match[1], serviceName: match[2].trim() }
    : { serviceCode: '', serviceName };
};

const matchesAny = (value, candidates) => candidates.some((candidate) => value === candidate || value.includes(candidate));

export const detectColumns = (rows) => {
  for (let r = 0; r < Math.min(rows.length, 80); r += 1) {
    const normalizedRow = (rows[r] || []).map((value) => normalizeText(value).toLowerCase());
    if (!normalizedRow.some(Boolean)) continue;
    let serviceCol = -1;
    let priceCol = -1;
    let codeCol = -1;
    let sourceClassificationCol = -1;
    normalizedRow.forEach((value, index) => {
      if (!value) return;
      if (serviceCol === -1 && !value.includes('كود') && matchesAny(value, [
        'service_name', 'اسم الخدمة عربي', 'اسم الخدمة', 'البيان بالعربي', 'الخدمة', 'الخدمه'
      ])) serviceCol = index;
      else if (priceCol === -1 && matchesAny(value, [
        'contract_price', 'unit_price', 'سعر العقد', 'متوسط السعر', 'السعر'
      ])) priceCol = index;
      else if (codeCol === -1 && matchesAny(value, [
        'service_code', 'الكود الأصلي', 'كود الخدمة', 'الكود', 'الرمز'
      ])) codeCol = index;
      else if (sourceClassificationCol === -1 && matchesAny(value, [
        'التصنيف التأميني', 'تصنيف المصدر', 'نوع المطالبة', 'التصنيف', 'القسم'
      ])) sourceClassificationCol = index;
    });
    if (serviceCol !== -1 && priceCol !== -1) {
      return { headerRow: r, serviceCol, priceCol, codeCol, sourceClassificationCol };
    }
  }
  return null;
};

const extractDetected = (sheetRows, sheetName, columns) => {
  const rows = [];
  let inheritedSourceClassification = '';
  for (let rowIndex = columns.headerRow + 1; rowIndex < sheetRows.length; rowIndex += 1) {
    const row = sheetRows[rowIndex] || [];
    const explicit = columns.sourceClassificationCol >= 0 ? normalizeText(row[columns.sourceClassificationCol]) : '';
    if (explicit) inheritedSourceClassification = explicit;
    const serviceName = normalizeText(row[columns.serviceCol]);
    if (!isLikelyServiceName(serviceName)) continue;
    const priceRange = parsePriceRange(row[columns.priceCol]);
    if (priceRange.min == null) continue;
    const identity = splitEmbeddedServiceCode(serviceName,
      columns.codeCol >= 0 ? normalizeText(row[columns.codeCol]) : '');
    rows.push({
      rowNumber: rowIndex + 1, sourceSheet: sheetName,
      sourceClassification: inheritedSourceClassification || sheetName,
      serviceName: identity.serviceName, serviceCode: identity.serviceCode,
      price: priceRange.min, minPrice: priceRange.min, maxPrice: priceRange.max, priceLabel: priceRange.label
    });
  }
  return rows;
};

const inferClassificationColumn = (sheetRows, headerRow) => {
  const counts = new Map();
  for (let rowIndex = headerRow + 1; rowIndex < Math.min(sheetRows.length, headerRow + 101); rowIndex += 1) {
    (sheetRows[rowIndex] || []).forEach((value, column) => {
      if (isKnownClassification(value)) counts.set(column, (counts.get(column) || 0) + 1);
    });
  }
  const best = [...counts.entries()].sort((left, right) => right[1] - left[1])[0];
  return best && best[1] >= 2 ? best[0] : -1;
};

const isKnownClassification = (value) => /^(إيواء|ايواء|عيادات خارجية|أشعة تحاليل رسوم أطباء|الرنين.*|أسنان روتيني|أسنان تجميلي|علاج طبيعي|تمريض منزلي)$/u.test(normalizeText(value));

const extractKnownHeaderlessProviderLayout = (sheetRows, sheetName) => {
  const rows = [];
  for (let rowIndex = 0; rowIndex < sheetRows.length; rowIndex += 1) {
    const row = sheetRows[rowIndex] || [];
    const serviceName = normalizeText(row[0]);
    const priceRange = parsePriceRange(row[2]);
    const sourceClassification = normalizeText(row[3]);
    if (!isLikelyServiceName(serviceName) || priceRange.min == null || !isKnownClassification(sourceClassification)) continue;
    rows.push({
      rowNumber: rowIndex + 1, sourceSheet: sheetName, sourceClassification,
      serviceName, serviceCode: normalizeText(row[1]), price: priceRange.min,
      minPrice: priceRange.min, maxPrice: priceRange.max, priceLabel: priceRange.label
    });
  }
  return rows;
};

const extractConservativeFallback = (sheetRows, sheetName) => {
  const rows = [];
  sheetRows.forEach((row, rowIndex) => {
    const cells = (row || []).map(normalizeText);
    if (!cells.some(Boolean)) return;
    const prices = cells.map((value, index) => ({ index, range: parsePriceRange(value) }))
      .filter((cell) => cell.range?.min != null && cell.range.min > 0);
    const services = cells.map((value, index) => ({ value, index })).filter((cell) => isLikelyServiceName(cell.value));
    if (!prices.length || !services.length) return;
    const service = services.reduce((best, current) => {
      const distance = Math.min(...prices.map((price) => Math.abs(price.index - current.index)));
      return !best || distance < best.distance ? { ...current, distance } : best;
    }, null);
    const price = prices.reduce((best, current) => {
      const distance = Math.abs(current.index - service.index);
      return !best || distance < best.distance ? { ...current, distance } : best;
    }, null);
    rows.push({ rowNumber: rowIndex + 1, sourceSheet: sheetName, sourceClassification: sheetName,
      serviceName: service.value, serviceCode: '', price: price.range.min,
      minPrice: price.range.min, maxPrice: price.range.max, priceLabel: price.range.label });
  });
  return rows;
};

export const extractRowsFromWorkbook = (workbook, XLSX) => {
  const rows = [];
  workbook.SheetNames.forEach((sheetName) => {
    const sheetRows = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], { header: 1, defval: '' });
    const columns = detectColumns(sheetRows);
    if (columns && columns.sourceClassificationCol < 0) {
      columns.sourceClassificationCol = inferClassificationColumn(sheetRows, columns.headerRow);
    }
    const headerless = columns ? [] : extractKnownHeaderlessProviderLayout(sheetRows, sheetName);
    rows.push(...(columns ? extractDetected(sheetRows, sheetName, columns)
      : headerless.length ? headerless : extractConservativeFallback(sheetRows, sheetName)));
  });
  const seen = new Set();
  return rows.filter((row) => {
    const key = `${row.sourceSheet}-${row.rowNumber}-${row.serviceName}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};
