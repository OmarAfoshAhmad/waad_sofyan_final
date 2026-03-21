import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  Grid,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography
} from '@mui/material';
import { UploadOutlined, DownloadOutlined } from '@ant-design/icons';
import * as XLSX from 'xlsx';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import systemSettingsService from 'services/api/systemSettings.service';

const DB_KEYS = {
  apiKey: 'AI_CLASSIFIER_API_KEY',
  model: 'AI_CLASSIFIER_MODEL',
  endpoint: 'AI_CLASSIFIER_ENDPOINT'
};

const DEFAULT_MODEL = 'qwen/qwen2.5-14b-instruct:free';
const DEFAULT_ENDPOINT = 'https://openrouter.ai/api/v1/chat/completions';
const AI_CONCURRENCY_LIMIT = 6;

const MAIN_CAT_MAP = {
  'إيواء': 'إيواء',
  'عيادات خارجية': 'عيادات خارجية',
  'اشعة': 'عيادات خارجية',
  'اشعة ': 'عيادات خارجية',
  'تحاليل طبية': 'عيادات خارجية',
  'علاج طبيعي': 'عيادات خارجية',
  'علاج طبيعي ': 'عيادات خارجية',
  'عمليات': 'إيواء',
  'عمليات ': 'إيواء',
  'اسنان تجميلي': 'عيادات خارجية',
  'اسنان وقائي': 'عيادات خارجية',
  'اسنان وقائي ': 'عيادات خارجية'
};

const SUB_CAT_MAP = {
  'اشعة': 'أشعة تحاليل رسوم أطباء',
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
  'كشف': 'عام',
  'مراجعة': 'عام',
  'معامل': 'أشعة تحاليل رسوم أطباء',
  'التخصص': ''
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
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
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

  // Reject rows where service cell is just a number/value and not a service label.
  if (numericOnly && !hasLetters) return false;

  // Reject rows where service value is the same numeric content as price column.
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

  // Prefer closest text/price pair on same row to handle mixed section layouts.
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

const fetchWithRetry = async (url, options, maxRetries = 2) => {
  let response;
  for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
    response = await fetch(url, options);
    if (response.status !== 429 || attempt === maxRetries) {
      return response;
    }

    const retryAfter = Number(response.headers.get('retry-after'));
    const waitMs = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * 1000 : 1000 * (2 ** attempt);
    await sleep(waitMs);
  }
  return response;
};

const runWithConcurrency = async (items, limit, worker) => {
  if (!items.length) return;

  let currentIndex = 0;
  const workers = Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, async () => {
    while (currentIndex < items.length) {
      if (worker.shouldStop?.()) {
        return;
      }
      const idx = currentIndex;
      currentIndex += 1;
      await worker(items[idx], idx);
    }
  });

  await Promise.all(workers);
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
      if (priceCol === -1 && (val.includes('contract_price') || val.includes('unit_price') || val.includes('price') || val.includes('السعر'))) {
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

const classify = (rawMain, rawSub) => {
  const main = normalize(rawMain);
  const sub = normalize(rawSub);

  const mainCandidate = MAIN_CAT_MAP[main] || main;
  const subCandidate = SUB_CAT_MAP[sub] || sub;

  const enforced = enforceSystemCategories(mainCandidate, subCandidate);
  const mappedMain = enforced.main;
  const mappedSub = enforced.sub;

  return { mappedMain, mappedSub };
};

const classifyWithAI = async ({ serviceName, rawMain, rawSub, aiConfig, cache, abortSignal }) => {
  const cacheKey = `${serviceName}__${rawMain}__${rawSub}`.toLowerCase();
  if (cache.has(cacheKey)) {
    return cache.get(cacheKey);
  }

  const fallback = classify(rawMain, rawSub);
  const fallbackResult = { ...fallback, method: 'fallback', confidence: 0.2, reason: '' };

  if (!aiConfig.enabled) {
    const result = { ...fallbackResult, reason: 'ai_disabled_missing_api_key' };
    cache.set(cacheKey, result);
    return result;
  }

  const payload = {
    model: aiConfig.model,
    temperature: 0,
    max_tokens: 120,
    response_format: { type: 'json_object' },
    messages: [
      {
        role: 'system',
        content:
          'You are a strict medical taxonomy classifier. Return strict JSON only with main_category, sub_category, confidence. Choose only from allowed categories; no creativity and no custom categories.'
      },
      {
        role: 'user',
        content: JSON.stringify({
          allowed_main_categories: Array.from(SYSTEM_MAIN),
          allowed_sub_categories: Array.from(SYSTEM_SUB),
          service_name: serviceName,
          source_main_hint: rawMain,
          source_sub_hint: rawSub
        })
      }
    ]
  };

  try {
    const response = await fetchWithRetry(
      aiConfig.endpoint,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${aiConfig.apiKey}`
        },
        body: JSON.stringify(payload),
        signal: abortSignal
      },
      2
    );

    if (!response.ok) {
      const details = await response.text();
      const friendly =
        response.status === 429
          ? 'ai_rate_limited_429: المزود المجاني مزدحم، حاول بعد قليل أو استخدم نموذج/رصيد آخر'
          : `ai_http_${response.status}${details ? `: ${details.slice(0, 160)}` : ''}`;
      const result = { ...fallbackResult, reason: friendly };
      cache.set(cacheKey, result);
      return result;
    }

    const data = await response.json();
    const content = data?.choices?.[0]?.message?.content;
    if (!content) {
      const result = { ...fallbackResult, reason: 'ai_empty_response' };
      cache.set(cacheKey, result);
      return result;
    }

    const parsed = JSON.parse(content);
    const mainCategory = normalize(parsed.main_category);
    const subCategory = normalize(parsed.sub_category);
    const confidence = Number(parsed.confidence);

    const result = {
      ...(() => {
        const enforced = enforceSystemCategories(mainCategory, subCategory);
        return {
          mappedMain: enforced.main,
          mappedSub: enforced.sub
        };
      })(),
      method: 'ai',
      confidence: Number.isNaN(confidence) ? 0.7 : Math.max(0, Math.min(1, confidence)),
      reason: ''
    };

    cache.set(cacheKey, result);
    return result;
  } catch (error) {
    if (error?.name === 'AbortError') {
      return { cancelled: true };
    }
    const result = { ...fallbackResult, reason: `ai_network_or_cors: ${error.message || 'unknown error'}` };
    cache.set(cacheKey, result);
    return result;
  }
};

const FacilityPricePreparationPage = () => {
  const [fileName, setFileName] = useState('');
  const [rowsPrepared, setRowsPrepared] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingAiConfig, setLoadingAiConfig] = useState(true);
  const [error, setError] = useState('');
  const [processStatus, setProcessStatus] = useState('');
  const [accuracy, setAccuracy] = useState(null);
  const [aiMeta, setAiMeta] = useState({ enabled: false, model: '', aiRows: 0, fallbackRows: 0, failureReason: '' });
  const [progress, setProgress] = useState({ current: 0, total: 0 });
  const [extractionReport, setExtractionReport] = useState(null);
  const cancelRequestedRef = useRef(false);
  const activeControllersRef = useRef(new Set());

  useEffect(() => {
    const loadAiStatus = async () => {
      try {
        setLoadingAiConfig(true);
        const settings = await systemSettingsService.getAll();
        const map = new Map((settings || []).map((s) => [s.settingKey, s.settingValue]));
        const apiKey = (map.get(DB_KEYS.apiKey) || '').trim();
        const model = (map.get(DB_KEYS.model) || DEFAULT_MODEL).trim() || DEFAULT_MODEL;
        setAiMeta((prev) => ({
          ...prev,
          enabled: Boolean(apiKey),
          model,
          failureReason: ''
        }));
      } catch (e) {
        setAiMeta((prev) => ({
          ...prev,
          enabled: false,
          failureReason: `تعذر تحميل إعدادات AI من قاعدة البيانات: ${e.message || 'unknown error'}`
        }));
      } finally {
        setLoadingAiConfig(false);
      }
    };
    loadAiStatus();
  }, []);

  const stats = useMemo(() => {
    if (!rowsPrepared.length) return null;

    let mainOk = 0;
    let subOk = 0;
    let bothOk = 0;
    let aiRows = 0;
    let confidenceSum = 0;
    const unknownMain = new Set();
    const unknownSub = new Set();

    rowsPrepared.forEach((r) => {
      const mOk = SYSTEM_MAIN.has(r.main_category);
      const sOk = SYSTEM_SUB.has(r.sub_category);
      if (r._method === 'ai') aiRows += 1;
      confidenceSum += Number(r._confidence || 0);
      if (mOk) mainOk += 1;
      else unknownMain.add(r.main_category);
      if (sOk) subOk += 1;
      else unknownSub.add(r.sub_category);
      if (mOk && sOk) bothOk += 1;
    });

    const total = rowsPrepared.length;
    const mainRatio = mainOk / total;
    const subRatio = subOk / total;
    const bothRatio = bothOk / total;
    const taxonomyConfidence = Number(((0.2 * mainRatio + 0.3 * subRatio + 0.5 * bothRatio) * 100).toFixed(2));
    const modelConfidence = Number(((confidenceSum / total) * 100).toFixed(2));
    const confidence = Math.min(taxonomyConfidence, modelConfidence);

    return {
      total,
      main: Number((mainRatio * 100).toFixed(2)),
      sub: Number((subRatio * 100).toFixed(2)),
      both: Number((bothRatio * 100).toFixed(2)),
      confidence,
      modelConfidence,
      taxonomyConfidence,
      aiRows,
      fallbackRows: total - aiRows,
      unknownMain: Array.from(unknownMain),
      unknownSub: Array.from(unknownSub)
    };
  }, [rowsPrepared]);

  const onFileChange = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setLoading(true);
    setError('');
    setProcessStatus('');
    setRowsPrepared([]);
    setFileName(file.name);
    setAccuracy(null);
    setExtractionReport(null);
    setAiMeta({ enabled: false, model: '', aiRows: 0, fallbackRows: 0, failureReason: '' });
    setProgress({ current: 0, total: 0 });
    cancelRequestedRef.current = false;
    activeControllersRef.current.clear();

    try {
      const settings = await systemSettingsService.getAll();
      const map = new Map((settings || []).map((s) => [s.settingKey, s.settingValue]));

      const apiKey = map.get(DB_KEYS.apiKey) || '';
      const model = map.get(DB_KEYS.model) || DEFAULT_MODEL;
      const endpoint = map.get(DB_KEYS.endpoint) || DEFAULT_ENDPOINT;
      const aiConfig = {
        apiKey: apiKey.trim(),
        model: model.trim() || DEFAULT_MODEL,
        endpoint: endpoint.trim() || DEFAULT_ENDPOINT,
        enabled: Boolean(apiKey.trim())
      };
      const cache = new Map();
      let failureReason = '';

      const buffer = await file.arrayBuffer();
      const workbook = XLSX.read(buffer, { type: 'array' });
      const firstSheetName = workbook.SheetNames[0];
      const sheet = workbook.Sheets[firstSheetName];
      const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, raw: true, defval: '' });

      const cols = detectColumns(rows);
      const prepared = [];
      const dataRows = Math.max(0, rows.length - (cols.headerRow + 1));
      setProgress({ current: 0, total: dataRows });

      const candidates = [];
      let directExtracted = 0;
      let inferredExtracted = 0;
      const rejectedReasons = {
        empty_or_header: 0,
        invalid_price: 0,
        invalid_service_name: 0,
        no_pair_detected: 0
      };

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
            if (!hasService) rejectedReasons.empty_or_header += 1;
            else if (!hasPrice || !validPrice) rejectedReasons.invalid_price += 1;
            else if (!validService) rejectedReasons.invalid_service_name += 1;
            else rejectedReasons.no_pair_detected += 1;
            continue;
          }
          service = inferred.service;
          priceRaw = inferred.price;
          inferredExtracted += 1;
        } else {
          directExtracted += 1;
        }

        if (!isLikelyValidServiceName(service, priceRaw)) {
          rejectedReasons.invalid_service_name += 1;
          continue;
        }

        const price = parseNumeric(priceRaw);
        if (price == null) {
          rejectedReasons.invalid_price += 1;
          continue;
        }

        candidates.push({ service, price, mainRaw, subRaw });
      }

      setExtractionReport({
        totalRowsScanned: dataRows,
        directExtracted,
        inferredExtracted,
        acceptedTotal: candidates.length,
        rejectedTotal: Math.max(0, dataRows - candidates.length),
        rejectedReasons
      });

      setProgress({ current: 0, total: candidates.length });
      let completed = 0;

      await runWithConcurrency(candidates, AI_CONCURRENCY_LIMIT, async (candidate, idx) => {
        if (cancelRequestedRef.current) {
          return;
        }

        const controller = new AbortController();
        activeControllersRef.current.add(controller);
        const codeMatch = candidate.service.match(CODE_PATTERN);
        const serviceCode = codeMatch ? codeMatch[0] : '';

        const result = await classifyWithAI({
          serviceName: candidate.service,
          rawMain: candidate.mainRaw,
          rawSub: candidate.subRaw,
          aiConfig,
          cache,
          abortSignal: controller.signal
        });
        activeControllersRef.current.delete(controller);

        if (result?.cancelled || cancelRequestedRef.current) {
          return;
        }

        const { mappedMain, mappedSub, method, confidence, reason } = result;

        if (!failureReason && aiConfig.enabled && method === 'fallback' && reason && reason !== 'ai_disabled_missing_api_key') {
          failureReason = reason;
        }

        prepared[idx] = {
          service_name: candidate.service,
          service_code: serviceCode,
          contract_price: candidate.price,
          main_category: mappedMain,
          sub_category: mappedSub,
          notes: '',
          _method: method,
          _confidence: confidence
        };

        completed += 1;
        if (completed % 5 === 0 || completed === candidates.length) {
          setProgress({ current: completed, total: candidates.length });
          setRowsPrepared(prepared.filter(Boolean));
        }
      }, {
        shouldStop: () => cancelRequestedRef.current
      });

      const finalized = prepared.filter(Boolean);

      if (!finalized.length) {
        if (cancelRequestedRef.current) {
          setProcessStatus('تم إيقاف العملية قبل اكتمال أي صف.');
          return;
        }
        throw new Error('لم يتم العثور على صفوف خدمات صالحة في الملف.');
      }

      setRowsPrepared(finalized);
      setAccuracy(true);
      const aiRows = finalized.filter((r) => r._method === 'ai').length;
      setAiMeta({
        enabled: aiConfig.enabled,
        model: aiConfig.model,
        aiRows,
        fallbackRows: finalized.length - aiRows,
        failureReason
      });

      if (cancelRequestedRef.current) {
        setProcessStatus(`تم إيقاف العملية. تم تجهيز ${finalized.length} صف ويمكن تنزيل الملف الحالي.`);
      }
    } catch (e) {
      setError(e.message || 'فشل قراءة الملف.');
    } finally {
      setLoading(false);
      activeControllersRef.current.clear();
    }
  };

  const onStopProcess = () => {
    if (!loading) return;
    cancelRequestedRef.current = true;
    activeControllersRef.current.forEach((controller) => controller.abort());
    activeControllersRef.current.clear();
    setProcessStatus('تم طلب إيقاف العملية. سيتم حفظ النتائج المنجزة حتى الآن.');
  };

  const downloadPrepared = () => {
    if (!rowsPrepared.length) return;

    const wsRows = [
      ['service_name / اسم الخدمة ★', 'service_code / الكود', 'contract_price / سعر العقد', 'main_category / التصنيف الرئيسي', 'sub_category / البند (التصنيف الفرعي)', 'notes / ملاحظات'],
      ['فحص شامل', 'MC-001', 100, 'عيادات خارجية', 'عام', 'مثال - احذف هذا الصف']
    ];

    rowsPrepared.forEach((r) => {
      wsRows.push([r.service_name, r.service_code, r.contract_price, r.main_category, r.sub_category, r.notes]);
    });

    const ws = XLSX.utils.aoa_to_sheet(wsRows);
    ws['!cols'] = [{ wch: 42 }, { wch: 16 }, { wch: 16 }, { wch: 26 }, { wch: 30 }, { wch: 30 }];

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Pricing_Template');

    const safeName = (fileName || 'facility_price_list').replace(/\.xlsx$/i, '');
    XLSX.writeFile(wb, `${safeName}_جاهز_للاستيراد.xlsx`);
  };

  return (
    <Box>
      <ModernPageHeader
        title="تجهيز قوائم أسعار المرافق الصحية"
        subtitle="نسخة تجريبية: تجهيز الملف قبل الاستيراد للقالب القياسي"
        icon={UploadOutlined}
      />

      <Card sx={{ mt: 2 }}>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              ارفع ملف الأسعار الأصلي (مثل ملف دار الشفاء)، وسيتم توليد ملف جاهز للاستيراد بالأعمدة المطلوبة فقط:
              اسم الخدمة، كود الخدمة، سعر العقد، التصنيف الرئيسي، التصنيف الفرعي، الملاحظات.
            </Typography>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <Button variant="contained" component="label" startIcon={<UploadOutlined />} disabled={loading}>
                رفع ملف Excel
                <input hidden type="file" accept=".xlsx,.xls" onChange={onFileChange} />
              </Button>

              <Button variant="outlined" startIcon={<DownloadOutlined />} onClick={downloadPrepared} disabled={!rowsPrepared.length}>
                تنزيل الملف الجاهز للاستيراد
              </Button>

              <Button variant="outlined" color="warning" onClick={onStopProcess} disabled={!loading}>
                إيقاف العملية
              </Button>
            </Stack>

            {loading && (
              <Stack direction="row" alignItems="center" spacing={1}>
                <CircularProgress size={18} />
                <Typography variant="body2">
                  جاري تحليل الملف... {progress.total > 0 ? `${Math.min(progress.current, progress.total)} / ${progress.total}` : ''}
                </Typography>
              </Stack>
            )}

            {!!error && <Alert severity="error">{error}</Alert>}
            {!!processStatus && <Alert severity="info">{processStatus}</Alert>}
            {loadingAiConfig ? (
              <Alert severity="info">جاري التحقق من إعدادات AI...</Alert>
            ) : (
              <Alert severity={aiMeta.enabled ? 'info' : 'warning'}>
                {aiMeta.enabled
                  ? `AI مفعل للتصنيف (${aiMeta.model}) | صفوف AI: ${aiMeta.aiRows} | صفوف fallback: ${aiMeta.fallbackRows}${aiMeta.failureReason ? ` | سبب fallback: ${aiMeta.failureReason}` : ''}`
                  : `AI غير مفعل: يرجى إدخال API Key من صفحة إعداد API للذكاء الاصطناعي${aiMeta.failureReason ? ` | ${aiMeta.failureReason}` : ''}`}
              </Alert>
            )}
            {accuracy && stats && (
              <Alert severity={stats.confidence >= 90 ? 'success' : 'warning'}>
                دقة التصنيف التقديرية: {stats.confidence}% | عدد الخدمات: {stats.total}
              </Alert>
            )}

            {extractionReport && (
              <Alert severity="info">
                تقرير الاستخراج: مباشر {extractionReport.directExtracted} | fallback ذكي {extractionReport.inferredExtracted} |
                مقبول {extractionReport.acceptedTotal} | مرفوض {extractionReport.rejectedTotal}
                {` | أسباب الرفض: ترويسة/فارغ ${extractionReport.rejectedReasons.empty_or_header}, سعر غير صالح ${extractionReport.rejectedReasons.invalid_price}, خدمة غير صالحة ${extractionReport.rejectedReasons.invalid_service_name}, بدون زوج خدمة/سعر ${extractionReport.rejectedReasons.no_pair_detected}`}
              </Alert>
            )}

            {stats && (
              <>
                <Divider />
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2">تطابق التصنيف الرئيسي</Typography>
                    <Typography variant="h6">{stats.main}%</Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2">تطابق التصنيف الفرعي</Typography>
                    <Typography variant="h6">{stats.sub}%</Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2">تطابق كامل (رئيسي + فرعي)</Typography>
                    <Typography variant="h6">{stats.both}%</Typography>
                  </Grid>
                </Grid>

                <Grid container spacing={2} sx={{ mt: 0.5 }}>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2">ثقة النموذج</Typography>
                    <Typography variant="h6">{stats.modelConfidence}%</Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2">ثقة تطابق التصنيفات</Typography>
                    <Typography variant="h6">{stats.taxonomyConfidence}%</Typography>
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <Typography variant="subtitle2">استخدام AI</Typography>
                    <Typography variant="h6">{stats.aiRows} / {stats.total}</Typography>
                  </Grid>
                </Grid>

                {(stats.unknownMain.length > 0 || stats.unknownSub.length > 0) && (
                  <Grid container spacing={2} sx={{ mt: 0.5 }}>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Typography variant="subtitle2">تصنيفات رئيسية غير معروفة</Typography>
                      <List dense>
                        {stats.unknownMain.map((v) => (
                          <ListItem key={v} sx={{ py: 0 }}>
                            <ListItemText primary={v} />
                          </ListItem>
                        ))}
                      </List>
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                      <Typography variant="subtitle2">تصنيفات فرعية غير معروفة</Typography>
                      <List dense>
                        {stats.unknownSub.map((v) => (
                          <ListItem key={v} sx={{ py: 0 }}>
                            <ListItemText primary={v} />
                          </ListItem>
                        ))}
                      </List>
                    </Grid>
                  </Grid>
                )}
              </>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};

export default FacilityPricePreparationPage;
