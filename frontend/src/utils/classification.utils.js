import * as XLSX from 'xlsx';

const MAIN_CAT_MAP = {
  إيواء: 'إيواء',
  'عيادات خارجية': 'عيادات خارجية',
  اشعة: 'عيادات خارجية',
  'اشعة ': 'عيادات خارجية',
  'تحاليل طبية': 'عيادات خارجية',
  'علاج طبيعي': 'عيادات خارجية',
  'علاج طبيعي ': 'عيادات خارجية',
  عمليات: 'إيواء',
  'عمليات ': 'إيواء',
  'اسنان تجميلي': 'عيادات خارجية',
  'اسنان وقائي': 'عيادات خارجية',
  'اسنان وقائي ': 'عيادات خارجية'
};

const SUB_CAT_MAP = {
  اشعة: 'أشعة تحاليل رسوم أطباء',
  'اشعة ': 'أشعة تحاليل رسوم أطباء',
  'خدمات الأسنان': 'أسنان روتيني',
  'خدمات العلاج الطبيعي': 'علاج طبيعي',
  'خدمات الرعاية بالعناية المركزه': 'عام',
  'خدمات الرعايه الطبيه': 'عام',
  'خدمات التخذير': 'عام',
  'خدمات الجراحة': 'عام',
  'خدمات الاذن والانف والحنجرة': 'عام',
  'خدمات الصور التشخيصية': 'أشعة تحاليل رسوم أطباء',
  'خدمات الطوارئ': 'عام',
  'خدمات العظام': 'عام',
  'خدمات العلاج الكيماوي': 'عام',
  'خدمات العيادات الخارجية': 'عام',
  'خدمات العيون': 'عام',
  'خدمات المناظير': 'عام',
  'خدمات تخطيط العصب': 'عام',
  'خدمات جراحة التجميل': 'عام',
  'خدمات جراحة الصدر': 'عام',
  'خدمات جلسات الغسيل': 'عام',
  كشف: 'عام',
  مراجعة: 'عام',
  معامل: 'أشعة تحاليل رسوم أطباء',
  التخصص: ''
};

const SYSTEM_MAIN = new Set(['إيواء', 'عيادات خارجية']);
const SYSTEM_SUB = new Set([
  'عام',
  'علاج طبيعي',
  'أسنان روتيني',
  'أسنان تجميلي',
  'أشعة تحاليل رسوم أطباء',
  'رنين مغناطيسي',
  'علاجات وأدوية روتينية',
  'أجهزة ومعدات',
  'النظارة الطبية'
]);

const CODE_PATTERN = /[A-Z]{2,4}-[A-Z0-9-]+/;

const normalize = (v) => (v == null ? '' : String(v).trim());
const AR_EN_LETTER_PATTERN = /[A-Za-z\u0600-\u06FF]/;
const NUMERIC_ONLY_PATTERN = /^[\d\s.,\-\/]+$/;

const enforceSystemCategories = (mainCandidate, subCandidate) => {
  const main = SYSTEM_MAIN.has(mainCandidate) ? mainCandidate : 'عيادات خارجية';
  const sub = SYSTEM_SUB.has(subCandidate) ? subCandidate : 'عام';
  return { main, sub };
};

const isLikelyValidServiceName = (service, priceRaw) => {
  const s = normalize(service);
  if (!s) return false;

  const hasLetters = AR_EN_LETTER_PATTERN.test(s);
  const numericOnly = NUMERIC_ONLY_PATTERN.test(s);

  if (numericOnly && !hasLetters) return false;

  const serviceAsNumber = Number(s.replace(/,/g, ''));
  const priceAsNumber = Number(priceRaw);
  if (Number.isFinite(serviceAsNumber) && Number.isFinite(priceAsNumber) && serviceAsNumber === priceAsNumber) {
    return false;
  }

  return true;
};

const HEADER_WORDS = ['البيان', 'القيمة', 'الكود', 'code', 'service', 'price', 'نوع التخطيط'];

const parseNumeric = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const s = normalize(value).replace(/,/g, '');
  if (!s) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
};

const isHeaderLikeText = (text) => {
  const t = normalize(text).toLowerCase();
  if (!t) return true;
  return HEADER_WORDS.some((w) => t.includes(w));
};

const inferServiceAndPriceFromRow = (row) => {
  const numericCandidates = [];
  const textCandidates = [];

  for (let i = 0; i < row.length; i += 1) {
    const raw = row[i];
    const text = normalize(raw);
    if (!text) continue;

    const n = parseNumeric(raw);
    if (n !== null && n > 0) {
      numericCandidates.push({ idx: i, value: n });
      continue;
    }

    if (AR_EN_LETTER_PATTERN.test(text) && !isHeaderLikeText(text) && text.length >= 3) {
      textCandidates.push({ idx: i, value: text });
    }
  }

  if (!numericCandidates.length || !textCandidates.length) {
    return null;
  }

  let best = null;
  for (const t of textCandidates) {
    for (const n of numericCandidates) {
      const distance = Math.abs(t.idx - n.idx);
      if (!best || distance < best.distance) {
        best = { service: t.value, price: n.value, distance };
      }
    }
  }

  return best ? { service: best.service, price: best.price } : null;
};

const detectColumns = (rows) => {
  for (let r = 0; r < Math.min(rows.length, 60); r += 1) {
    const row = (rows[r] || []).map((v) => normalize(v).toLowerCase());
    if (!row.some(Boolean)) continue;

    let serviceCol = -1;
    let priceCol = -1;
    let subCol = -1;
    let mainCol = -1;

    row.forEach((val, idx) => {
      if (!val) return;
      if (serviceCol === -1 && (val.includes('service_name') || val.includes('اسم الخدمة') || val.includes('الخدمه') || val === 'الخدمة')) {
        serviceCol = idx;
      }
      if (
        priceCol === -1 &&
        (val.includes('contract_price') || val.includes('unit_price') || val.includes('price') || val.includes('السعر'))
      ) {
        priceCol = idx;
      }
      if (subCol === -1 && (val.includes('sub_category') || val.includes('التصنيف الفرعي') || val.includes('التخصص'))) {
        subCol = idx;
      }
      if (mainCol === -1 && (val.includes('main_category') || val.includes('التصنيف الرئيسي') || val === 'category')) {
        mainCol = idx;
      }
    });

    if (serviceCol !== -1 && priceCol !== -1) {
      if (mainCol === -1) {
        const knownMainValues = new Set([
          'إيواء',
          'عيادات خارجية',
          'عمليات',
          'عمليات ',
          'اشعة',
          'اشعة ',
          'تحاليل طبية',
          'علاج طبيعي',
          'علاج طبيعي ',
          'اسنان تجميلي',
          'اسنان وقائي',
          'اسنان وقائي '
        ]);

        let bestCol = -1;
        let bestHits = 0;
        for (let c = 0; c < row.length; c += 1) {
          if (c === serviceCol || c === priceCol || c === subCol) continue;
          let hits = 0;
          for (let rr = r + 1; rr < Math.min(rows.length, r + 120); rr += 1) {
            const val = normalize(rows[rr]?.[c]);
            if (knownMainValues.has(val)) hits += 1;
          }
          if (hits > bestHits) {
            bestHits = hits;
            bestCol = c;
          }
        }
        if (bestCol !== -1 && bestHits >= 3) {
          mainCol = bestCol;
        }
      }

      return { headerRow: r, serviceCol, priceCol, subCol, mainCol };
    }
  }

  return { headerRow: 9, serviceCol: 3, priceCol: 2, subCol: 4, mainCol: 5 };
};

export const classify = (rawMain, rawSub, serviceName = '') => {
  let main = normalize(rawMain);
  let sub = normalize(rawSub);

  let confidence = 0.3; // Default low confidence if totally missing

  if (main) {
    if (SYSTEM_MAIN.has(main)) {
      confidence = 1.0; // High confidence (Exact match)
    } else if (MAIN_CAT_MAP[main]) {
      confidence = 0.8; // Medium confidence (Mapped from known alias)
    } else {
      confidence = 0.5; // Low confidence (Unknown alias, had to enforce default)
    }
  } else {
    const s = normalize(serviceName).toLowerCase();
    if (s) {
      if (
        s.includes('جلسة') ||
        s.includes('تخطيط') ||
        s.includes('علاج طبيعي') ||
        s.includes('اشعة') ||
        s.includes('أشعة') ||
        s.includes('تحليل') ||
        s.includes('كشف') ||
        s.includes('مراجعة') ||
        s.includes('صورة') ||
        s.includes('رنين') ||
        s.includes('حقن') ||
        s.includes('injection') ||
        s.includes('aspiration') ||
        s.includes('study')
      ) {
        main = 'عيادات خارجية';
        confidence = 0.7;

        if (s.includes('اسنان') || s.includes('أسنان') || s.includes('حشو') || s.includes('خلع')) {
          sub = 'أسنان روتيني';
        } else if (s.includes('نظارة') || s.includes('عدسة') || s.includes('بصريات')) {
          sub = 'النظارة الطبية';
        } else if (
          s.includes('اشعة') ||
          s.includes('أشعة') ||
          s.includes('تحليل') ||
          s.includes('تحاليل') ||
          s.includes('رنين') ||
          s.includes('تخطيط')
        ) {
          sub = 'أشعة تحاليل رسوم أطباء';
        } else if (s.includes('علاج طبيعي') || s.includes('جلسة')) {
          sub = 'علاج طبيعي';
        }
      } else if (
        s.includes('عملية') ||
        s.includes('جراحة') ||
        s.includes('تخدير') ||
        s.includes('عناية') ||
        s.includes('تنويم') ||
        s.includes('surgery')
      ) {
        main = 'إيواء';
        confidence = 0.7;
      }
    }
  }

  const mainCandidate = MAIN_CAT_MAP[main] || main;
  const subCandidate = SUB_CAT_MAP[sub] || sub;

  const enforced = enforceSystemCategories(mainCandidate, subCandidate);
  const mappedMain = enforced.main;
  const mappedSub = enforced.sub;

  return { mappedMain, mappedSub, confidence };
};

export const parseExcelPriceList = async (file) => {
  const buffer = await file.arrayBuffer();
  const workbook = XLSX.read(buffer, { type: 'array' });
  const firstSheetName = workbook.SheetNames[0];
  const sheet = workbook.Sheets[firstSheetName];
  const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, raw: true, defval: '' });

  const cols = detectColumns(rows);
  const candidates = [];

  for (let r = cols.headerRow + 1; r < rows.length; r += 1) {
    const row = rows[r] || [];
    let service = normalize(row[cols.serviceCol]);
    let priceRaw = row[cols.priceCol];
    const subRaw = cols.subCol >= 0 ? normalize(row[cols.subCol]) : '';
    const mainRaw = cols.mainCol >= 0 ? normalize(row[cols.mainCol]) : '';

    const hasService = !!service && !['الخدمه', 'الخدمة', 'service_name', 'service'].includes(service.toLowerCase());
    const hasPrice = priceRaw !== '' && priceRaw != null;
    const validPrice = parseNumeric(priceRaw) !== null;
    const validService = isLikelyValidServiceName(service, priceRaw);

    const isDefaultPairValid = hasService && hasPrice && validService && validPrice;

    if (!isDefaultPairValid) {
      const inferred = inferServiceAndPriceFromRow(row);
      if (!inferred) {
        continue;
      }
      service = inferred.service;
      priceRaw = inferred.price;
    }

    if (!isLikelyValidServiceName(service, priceRaw)) {
      continue;
    }

    const price = parseNumeric(priceRaw);
    if (price == null) {
      continue;
    }

    const codeMatch = service.match(CODE_PATTERN);
    const serviceCode = codeMatch ? codeMatch[0] : '';

    // Fast local classification fallback
    const { mappedMain, mappedSub, confidence } = classify(mainRaw, subRaw, service);

    candidates.push({
      id: `item-${r}`, // Add a unique ID for React keys and editing

      serviceName: service,
      serviceCode: serviceCode,
      contractPrice: price,
      mainCategory: mappedMain,
      subCategory: mappedSub,
      rawMain: mainRaw,
      rawSub: subRaw,
      confidenceScore: confidence,
      isEdited: false
    });
  }

  return candidates;
};
