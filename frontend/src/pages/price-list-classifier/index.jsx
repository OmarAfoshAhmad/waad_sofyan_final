import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  LinearProgress,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import Checkbox from '@mui/material/Checkbox';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import PsychologyAltIcon from '@mui/icons-material/PsychologyAlt';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';
import PlaylistAddCheckIcon from '@mui/icons-material/PlaylistAddCheck';
import LibraryAddCheckIcon from '@mui/icons-material/LibraryAddCheck';
import { useSearchParams } from 'react-router-dom';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';
import providersService from 'services/api/providers.service';

const loadXlsx = async () => import('xlsx');

const normalizeText = (value) => (value == null ? '' : String(value).trim());
const numericPattern = /^[\d\s.,]+$/;
const letterPattern = /[A-Za-z\u0600-\u06FF]/;
const headerOnlyWords = [
  'service_name',
  'اسم الخدمة',
  'اسم الخدمة عربي',
  'اسم الخدمة إنجليزي',
  'medicalserviceslistemultinature',
  'contract_price',
  'service_code',
  'medical_category_code',
  'medical_category_name',
  'السعر',
  'الكود',
  'التصنيف',
  'الخدمات'
];

const parseNumber = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const text = normalizeText(value).replace(/,/g, '');
  if (!text) return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
};

const parsePriceRange = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return { min: value, max: value, label: value };
  }

  const raw = normalizeText(value);
  if (!raw) return { min: null, max: null, label: '' };

  const normalized = raw
    .replace(/,/g, '')
    .replace(/[–—−]/g, '-')
    .replace(/\b(to)\b/gi, '-')
    .replace(/إلى|الى|لغاية|حتى|من/gi, '-')
    .replace(/[^\d.\-\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  const numbers = normalized
    .split(/[\s-]+/)
    .map((part) => Number(part))
    .filter((number) => Number.isFinite(number) && number > 0);

  if (!numbers.length) {
    const single = parseNumber(raw);
    return single == null ? { min: null, max: null, label: raw } : { min: single, max: single, label: single };
  }

  const min = Math.min(...numbers);
  const max = Math.max(...numbers);
  return {
    min,
    max,
    label: min === max ? min : `${min}-${max}`
  };
};

const isLikelyServiceName = (value) => {
  const text = normalizeText(value);
  if (text.length < 3) return false;
  if (text.length > 220) return false;
  if (!letterPattern.test(text)) return false;
  if (numericPattern.test(text)) return false;
  const lower = text.toLowerCase();
  if (headerOnlyWords.some((word) => lower === word.toLowerCase() || lower.includes(`${word.toLowerCase()}:`))) return false;
  if (lower.endsWith('.xlsx') || lower.endsWith('.xls')) return false;
  if (lower.includes('خدمات ') && lower.includes('xlsx')) return false;
  return true;
};

const detectColumns = (rows) => {
  for (let r = 0; r < Math.min(rows.length, 80); r += 1) {
    const normalizedRow = (rows[r] || []).map((value) => normalizeText(value).toLowerCase());
    if (!normalizedRow.some(Boolean)) continue;

    let serviceCol = -1;
    let priceCol = -1;
    let codeCol = -1;

    normalizedRow.forEach((value, index) => {
      if (!value) return;
      if (
        serviceCol === -1 &&
        (value.includes('service_name') ||
          value.includes('اسم الخدمة عربي') ||
          value.includes('اسم الخدمة') ||
          value === 'الخدمة' ||
          value === 'البيان')
      ) {
        serviceCol = index;
      } else if (
        priceCol === -1 &&
        (value.includes('contract_price') ||
          value.includes('unit_price') ||
          value.includes('سعر العقد') ||
          value === 'السعر' ||
          value.includes('السعر'))
      ) {
        priceCol = index;
      } else if (codeCol === -1 && (value.includes('service_code') || value === 'الكود' || value.includes('الكود الأصلي'))) {
        codeCol = index;
      }
    });

    if (serviceCol !== -1) {
      return { headerRow: r, serviceCol, priceCol, codeCol };
    }
  }

  return null;
};

const extractRowsByDetectedColumns = (sheetRows, sheetName, columns) => {
  const rows = [];
  for (let rowIndex = columns.headerRow + 1; rowIndex < sheetRows.length; rowIndex += 1) {
    const row = sheetRows[rowIndex] || [];
    const serviceName = normalizeText(row[columns.serviceCol]);
    if (!isLikelyServiceName(serviceName)) continue;

    const priceRange = columns.priceCol >= 0 ? parsePriceRange(row[columns.priceCol]) : { min: null, max: null, label: '' };
    const serviceCode = columns.codeCol >= 0 ? normalizeText(row[columns.codeCol]) : '';

    rows.push({
      rowNumber: rowIndex + 1,
      sourceSheet: sheetName,
      serviceName,
      serviceCode,
      price: priceRange.min,
      minPrice: priceRange.min,
      maxPrice: priceRange.max,
      priceLabel: priceRange.label
    });
  }
  return rows;
};

const extractRowsByConservativeFallback = (sheetRows, sheetName) => {
  const rows = [];
  sheetRows.forEach((row, rowIndex) => {
    const cells = (row || []).map(normalizeText);
    if (!cells.some(Boolean)) return;

    const priceCandidates = cells
      .map((value, index) => ({ value, index, range: parsePriceRange(value) }))
      .filter((cell) => cell.range?.min != null && cell.range.min > 0);
    if (!priceCandidates.length) return;

    const serviceCandidates = cells.map((value, index) => ({ value, index })).filter((cell) => isLikelyServiceName(cell.value));
    if (!serviceCandidates.length) return;

    const service = serviceCandidates.reduce((best, current) => {
      const nearestDistance = Math.min(...priceCandidates.map((price) => Math.abs(price.index - current.index)));
      if (!best || nearestDistance < best.distance) return { ...current, distance: nearestDistance };
      return best;
    }, null);

    const nearestPrice = priceCandidates.reduce((best, current) => {
      const distance = Math.abs(current.index - service.index);
      if (!best || distance < best.distance) return { ...current, distance };
      return best;
    }, null);

    rows.push({
      rowNumber: rowIndex + 1,
      sourceSheet: sheetName,
      serviceName: service.value,
      serviceCode: '',
      price: nearestPrice?.range?.min ?? null,
      minPrice: nearestPrice?.range?.min ?? null,
      maxPrice: nearestPrice?.range?.max ?? null,
      priceLabel: nearestPrice?.range?.label ?? ''
    });
  });
  return rows;
};

const extractRowsFromWorkbook = (workbook, XLSX) => {
  const rows = [];

  workbook.SheetNames.forEach((sheetName) => {
    const sheetRows = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], { header: 1, defval: '' });
    const columns = detectColumns(sheetRows);
    const extracted = columns
      ? extractRowsByDetectedColumns(sheetRows, sheetName, columns)
      : extractRowsByConservativeFallback(sheetRows, sheetName);
    rows.push(...extracted);
  });

  const seen = new Set();
  return rows.filter((row) => {
    const key = `${row.sourceSheet}-${row.rowNumber}-${row.serviceName}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const statusColor = {
  AUTO_APPROVED: 'success',
  STRONG_SUGGESTION: 'warning',
  REVIEW_REQUIRED: 'warning',
  SPLIT_REQUIRED: 'error',
  QUARANTINED_NON_SERVICE: 'default',
  EXCLUDED_COSMETIC: 'error',
  HIGH_CONFIDENCE: 'success',
  NEEDS_REVIEW: 'warning',
  UNKNOWN: 'error'
};

const v50StatusLabel = {
  AUTO_APPROVED: 'معتمد آلياً — V50',
  STRONG_SUGGESTION: 'اقتراح قوي — يحتاج مراجعة',
  REVIEW_REQUIRED: 'يحتاج مراجعة بشرية',
  SPLIT_REQUIRED: 'يجب تقسيم السطر',
  QUARANTINED_NON_SERVICE: 'ليس خدمة طبية',
  EXCLUDED_COSMETIC: 'تجميلي مستبعد',
  HIGH_CONFIDENCE: 'تصنيف قديم',
  NEEDS_REVIEW: 'تحتاج مراجعة',
  UNKNOWN: 'غير معروف'
};

const sessionStatusLabel = {
  DRAFT: 'مسودة',
  CLASSIFIED: 'مصنفة',
  NEEDS_REVIEW: 'تحتاج مراجعة',
  READY_TO_POST: 'جاهزة للترحيل',
  POSTED_TO_CONTRACT: 'مرحّلة لعقد',
  SUPERSEDED: 'استُبدلت',
  CANCELLED: 'ملغاة'
};

const CLASSIFICATION_BATCH_SIZE = 500;
const CLASSIFICATION_SESSION_KEY = 'waad.priceListClassifier.session.v1';

const rowKey = (item) => `${item.sourceSheet || '-'}-${item.rowNumber || '-'}-${item.serviceName || '-'}`;

const getEffectiveCategory = (item) => item.manualCategory || item.bestMatch;

const getEffectiveStatusLabel = (item) => (item.manualCategory ? 'معتمد يدوياً' : v50StatusLabel[item.status] || item.statusLabel);

const getCanonicalExportName = (item) => item.bestMatch?.canonicalName || '';
const getProviderServiceName = (item) => item.serviceName || item.bestMatch?.canonicalName || '';

const getPriceMin = (item) => {
  const candidates = [item.minPrice, item.price].map(Number).filter((value) => Number.isFinite(value) && value > 0);
  return candidates.length ? Math.min(...candidates) : null;
};

const getPriceMax = (item) => {
  const candidates = [item.maxPrice, item.minPrice, item.price].map(Number).filter((value) => Number.isFinite(value) && value > 0);
  return candidates.length ? Math.max(...candidates) : null;
};

const hasPriceRange = (item) => {
  const min = getPriceMin(item);
  const max = getPriceMax(item);
  return min != null && max != null && Number(max) > Number(min);
};

const formatContractPrice = (min, max) => {
  if (min == null) return '';
  if (max == null || Number(max) === Number(min)) return min;
  return `${min}-${max}`;
};

const normalizeMergeKeyPart = (value) =>
  normalizeText(value)
    .toLowerCase()
    .replace(/[ـًٌٍَُِّْ]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

const buildContractReadyRows = (items) => {
  const acceptedItems = items.filter((item) => getEffectiveCategory(item)?.medicalCategoryId);
  const groups = new Map();

  acceptedItems.forEach((item) => {
    const effectiveCategory = getEffectiveCategory(item);
    const providerServiceName = getProviderServiceName(item);
    const canonicalServiceName = getCanonicalExportName(item);
    const key = [
      normalizeMergeKeyPart(providerServiceName),
      effectiveCategory?.medicalCategoryCode || effectiveCategory?.medicalCategoryId || ''
    ].join('|');

    if (!groups.has(key)) {
      groups.set(key, {
        service_name: providerServiceName,
        service_code: item.serviceCode || '',
        canonical_service_name: canonicalServiceName,
        minPrice: getPriceMin(item),
        maxPrice: getPriceMax(item),
        medical_category_code: effectiveCategory?.medicalCategoryCode || '',
        medical_category_name: effectiveCategory?.medicalCategoryName || '',
        providerCodes: new Set(),
        canonicalNames: new Set(),
        sourceRefs: [],
        sourceKeys: [],
        rawStatuses: new Set(),
        confidenceValues: [],
        statuses: new Set()
      });
    }

    const group = groups.get(key);
    const min = getPriceMin(item);
    const max = getPriceMax(item);
    if (min != null) group.minPrice = group.minPrice == null ? min : Math.min(group.minPrice, min);
    if (max != null) group.maxPrice = group.maxPrice == null ? max : Math.max(group.maxPrice, max);
    if (item.serviceCode) group.providerCodes.add(item.serviceCode);
    if (canonicalServiceName) group.canonicalNames.add(canonicalServiceName);
    if (!group.service_code && item.serviceCode) group.service_code = item.serviceCode;
    group.sourceRefs.push(`${item.sourceSheet || '-'} صف ${item.rowNumber || '-'}`);
    group.sourceKeys.push(rowKey(item));
    if (item.status) group.rawStatuses.add(item.status);
    if (item.bestMatch?.confidence != null) group.confidenceValues.push(item.bestMatch.confidence);
    group.statuses.add(getEffectiveStatusLabel(item));
  });

  const resolveMergedStatus = (rawStatuses) => {
    const statuses = new Set(rawStatuses || []);
    if (statuses.has('QUARANTINED_NON_SERVICE')) return 'QUARANTINED_NON_SERVICE';
    if (statuses.has('EXCLUDED_COSMETIC')) return 'EXCLUDED_COSMETIC';
    if (statuses.has('SPLIT_REQUIRED')) return 'SPLIT_REQUIRED';
    if (statuses.has('REVIEW_REQUIRED')) return 'REVIEW_REQUIRED';
    if (statuses.has('STRONG_SUGGESTION')) return 'STRONG_SUGGESTION';
    if (statuses.has('UNKNOWN') || statuses.has('NEEDS_REVIEW')) return 'REVIEW_REQUIRED';
    return 'AUTO_APPROVED';
  };

  const statusLabel = {
    ...v50StatusLabel
  };

  return Array.from(groups.values()).map((group) => {
    const rawStatuses = Array.from(group.rawStatuses);
    const displayStatus = resolveMergedStatus(rawStatuses);
    return {
      sourceKeys: group.sourceKeys,
      rawStatuses,
      display_status: displayStatus,
      display_status_label: statusLabel[displayStatus] || displayStatus,
      confidence: group.confidenceValues.length ? Math.max(...group.confidenceValues) : null,
      service_name: group.service_name,
      service_code: group.service_code,
      canonical_service_name: group.canonical_service_name || Array.from(group.canonicalNames)[0] || '',
      contract_price: formatContractPrice(group.minPrice, group.maxPrice),
      medical_category_code: group.medical_category_code,
      medical_category_name: group.medical_category_name,
      notes: [
        group.providerCodes.size > 1 ? `أكواد المرفق المرتبطة: ${Array.from(group.providerCodes).join('، ')}` : '',
        group.canonicalNames.size > 1 ? `أسماء موحدة مقترحة: ${Array.from(group.canonicalNames).join('، ')}` : '',
        group.canonicalNames.size === 1 ? `الاسم الموحد: ${Array.from(group.canonicalNames)[0]}` : '',
        group.minPrice != null && group.maxPrice != null && group.minPrice !== group.maxPrice
          ? `مجال سعري من التكرارات/المصدر: ${group.minPrice}-${group.maxPrice}`
          : '',
        group.confidenceValues.length ? `أعلى ثقة: ${Math.max(...group.confidenceValues)}` : '',
        `الحالة: ${Array.from(group.statuses).join('، ')}`,
        `المصادر: ${group.sourceRefs.join(' | ')}`
      ]
        .filter(Boolean)
        .join(' | ')
    };
  });
};

const buildContractRowsWithoutMerge = (items) =>
  items
    .filter((item) => getEffectiveCategory(item)?.medicalCategoryId)
    .map((item) => {
      const effectiveCategory = getEffectiveCategory(item);
      const min = getPriceMin(item);
      const max = getPriceMax(item);
      const canonicalServiceName = getCanonicalExportName(item);
      return {
        sourceKeys: [rowKey(item)],
        rawStatuses: item.status ? [item.status] : [],
        display_status: item.status || 'REVIEW_REQUIRED',
        display_status_label: getEffectiveStatusLabel(item),
        confidence: item.bestMatch?.confidence ?? null,
        service_name: getProviderServiceName(item),
        service_code: item.serviceCode || '',
        canonical_service_name: canonicalServiceName,
        contract_price: formatContractPrice(min, max),
        medical_category_code: effectiveCategory?.medicalCategoryCode || '',
        medical_category_name: effectiveCategory?.medicalCategoryName || '',
        notes: [
          canonicalServiceName ? `الاسم الموحد: ${canonicalServiceName}` : '',
          min != null && max != null && min !== max ? `مجال سعري من المصدر: ${min}-${max}` : '',
          item.bestMatch?.confidence != null ? `الثقة: ${item.bestMatch.confidence}` : '',
          `الحالة: ${getEffectiveStatusLabel(item)}`,
          `المصدر: ${item.sourceSheet || '-'} صف ${item.rowNumber || '-'}`
        ]
          .filter(Boolean)
          .join(' | ')
      };
    });

const buildSummary = (items) => ({
  total: items.length,
  highConfidence: items.filter((item) => item.status === 'AUTO_APPROVED').length,
  needsReview: items.filter((item) => ['STRONG_SUGGESTION', 'REVIEW_REQUIRED', 'SPLIT_REQUIRED'].includes(item.status)).length,
  unknown: items.filter((item) => ['QUARANTINED_NON_SERVICE', 'EXCLUDED_COSMETIC'].includes(item.status)).length,
  duplicateNames: items.filter((item) => item.duplicateName).length,
  rangedPrices: items.filter((item) => hasPriceRange(item)).length
});

const mapBackendSessionItems = (session) =>
  (session?.items || []).map((item) => ({
    rowNumber: item.rowNumber,
    sourceSheet: item.sourceSheet,
    serviceCode: item.serviceCode,
    serviceName: item.serviceName,
    price: item.price ?? item.minPrice,
    minPrice: item.minPrice ?? item.price,
    maxPrice: item.maxPrice ?? item.minPrice ?? item.price,
    priceLabel: item.priceLabel,
    status: item.status,
    statusLabel: item.status,
    dictionaryReleaseId: item.dictionaryReleaseId,
    dictionaryVersion: item.dictionaryVersion,
    conceptCode: item.dictionaryConceptCode,
    matchMethod: item.classificationMethod,
    reason: item.classificationReason,
    exceptionType: item.classificationExceptionType,
    evidenceId: item.classificationEvidenceId,
    excludeFromPrecision: Boolean(item.classificationExcludePrecision),
    duplicateName: Boolean(item.duplicateName),
    manualCategory: item.medicalCategoryId
      ? {
          medicalCategoryId: item.medicalCategoryId,
          medicalCategoryCode: item.medicalCategoryCode,
          medicalCategoryName: item.medicalCategoryName
        }
      : null,
    bestMatch: item.medicalCategoryId
      ? {
          entryId: item.dictionaryEntryId,
          canonicalName: item.canonicalName,
          medicalCategoryId: item.medicalCategoryId,
          medicalCategoryCode: item.medicalCategoryCode,
          medicalCategoryName: item.medicalCategoryName,
          confidence: item.confidence
        }
      : null
  }));

const saveClassificationSession = (session) => {
  try {
    localStorage.setItem(CLASSIFICATION_SESSION_KEY, JSON.stringify({ ...session, savedAt: new Date().toISOString() }));
  } catch {
    // Local persistence is a convenience; classification must continue even if storage quota is full.
  }
};

const loadClassificationSession = () => {
  try {
    const raw = localStorage.getItem(CLASSIFICATION_SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

const clearClassificationSession = () => {
  try {
    localStorage.removeItem(CLASSIFICATION_SESSION_KEY);
  } catch {
    // ignore
  }
};

const exportRows = async (items) => {
  const XLSX = await loadXlsx();
  const data = items.map((item) => ({
    sheet: item.sourceSheet,
    row_number: item.rowNumber,
    provider_service_code: item.serviceCode || '',
    provider_service_name: item.serviceName,
    original_price: item.priceLabel || (item.price ?? ''),
    min_price: getPriceMin(item) ?? '',
    max_price: getPriceMax(item) ?? '',
    classification_status: getEffectiveStatusLabel(item),
    confidence: item.bestMatch?.confidence ?? '',
    canonical_name: item.bestMatch?.canonicalName ?? '',
    medical_category_code: getEffectiveCategory(item)?.medicalCategoryCode ?? '',
    medical_category_name: getEffectiveCategory(item)?.medicalCategoryName ?? '',
    matched_text: item.bestMatch?.matchedText ?? '',
    manual_override: item.manualCategory ? 'YES' : 'NO',
    duplicate_name: item.duplicateName ? 'YES' : 'NO'
  }));

  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet(data);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'classified_price_list');
  XLSX.writeFile(workbook, 'تصنيف_قائمة_أسعار_بالقاموس.xlsx');
};

const appendCategoriesLookupSheet = (XLSX, workbook, categories = []) => {
  const lookup = categories.map((category) => ({
    medical_category_id: category.id,
    medical_category_code: category.code || '',
    medical_category_name: category.nameAr || category.name || '',
    medical_category_name_en: category.nameEn || ''
  }));
  const worksheet = XLSX.utils.json_to_sheet(lookup);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'التصنيفات المتاحة');
};

const stripInternalContractRowFields = (rows = []) =>
  rows.map(({ sourceKeys, rawStatuses, display_status, display_status_label, confidence, ...row }) => row);

const exportProviderContractReadyRows = async (items, categories = [], mergeDuplicates = true) => {
  const XLSX = await loadXlsx();
  const data = stripInternalContractRowFields(mergeDuplicates ? buildContractReadyRows(items) : buildContractRowsWithoutMerge(items));

  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet(data);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Pricing_Template');
  appendCategoriesLookupSheet(XLSX, workbook, categories);
  XLSX.writeFile(workbook, 'قائمة_أسعار_جاهزة_مبدئياً_لعقد_مقدم_خدمة.xlsx');
};

const downloadTemplate = async (categories = []) => {
  const XLSX = await loadXlsx();
  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.json_to_sheet([
    {
      service_name: 'مثال: تحليل CBC',
      service_code: 'SRV-001',
      contract_price: 25,
      medical_category_code: 'CAT-LAB',
      medical_category_name: 'التحاليل الطبية والمختبرات',
      notes: 'اختياري'
    },
    {
      service_name: 'مثال: رنين مغناطيسي',
      service_code: 'SRV-002',
      contract_price: 900,
      medical_category_code: 'CAT-IMG-ADV',
      medical_category_name: 'التصوير بالرنين المغناطيسي والمقطعي والطبقي',
      notes: 'اختياري'
    }
  ]);
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Pricing_Template');
  appendCategoriesLookupSheet(XLSX, workbook, categories);
  XLSX.writeFile(workbook, 'قالب_تنظيم_قائمة_الأسعار.xlsx');
};

export default function PriceListClassifierPage() {
  const [searchParams] = useSearchParams();
  const [fileName, setFileName] = useState('');
  const [rawRows, setRawRows] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [classificationProgress, setClassificationProgress] = useState(null);
  const [promoting, setPromoting] = useState(false);
  const [approvingSynonyms, setApprovingSynonyms] = useState(false);
  const [savingSession, setSavingSession] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [priceFilter, setPriceFilter] = useState('ALL');
  const [mergeDuplicatesForContracts, setMergeDuplicatesForContracts] = useState(true);
  const [categories, setCategories] = useState([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [sessionInfo, setSessionInfo] = useState(null);
  const [providers, setProviders] = useState([]);
  const [selectedProvider, setSelectedProvider] = useState(null);

  const items = useMemo(() => result?.items || [], [result?.items]);
  const manualReviewedCount = items.filter((item) => item.manualCategory).length;
  const synonymReadyCount = items.filter(
    (item) => item.bestMatch?.entryId && item.serviceName && item.serviceName !== item.bestMatch?.canonicalName
  ).length;
  const mergedContractRows = useMemo(() => buildContractReadyRows(items), [items]);
  const unmergedContractRows = useMemo(() => buildContractRowsWithoutMerge(items), [items]);
  const contractDisplayRows = mergeDuplicatesForContracts ? mergedContractRows : unmergedContractRows;
  const filteredMergedContractRows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return contractDisplayRows.filter((row) => {
      if (statusFilter !== 'ALL' && row.display_status !== statusFilter) return false;
      const priceRange = parsePriceRange(row.contract_price);
      const isRange = priceRange.min != null && priceRange.max != null && Number(priceRange.max) > Number(priceRange.min);
      if (priceFilter === 'RANGE' && !isRange) return false;
      if (priceFilter === 'SINGLE' && (isRange || priceRange.min == null)) return false;
      if (priceFilter === 'MISSING' && priceRange.min != null) return false;
      if (!q) return true;
      return [
        row.service_code,
        row.service_name,
        row.canonical_service_name,
        row.medical_category_code,
        row.medical_category_name,
        row.notes
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q));
    });
  }, [contractDisplayRows, search, statusFilter, priceFilter]);
  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter((item) => {
      if (statusFilter !== 'ALL' && item.status !== statusFilter) return false;
      if (priceFilter === 'RANGE' && !hasPriceRange(item)) return false;
      if (priceFilter === 'SINGLE' && (hasPriceRange(item) || getPriceMin(item) == null)) return false;
      if (priceFilter === 'MISSING' && getPriceMin(item) != null) return false;
      if (!q) return true;
      return [
        item.serviceCode,
        item.serviceName,
        item.bestMatch?.canonicalName,
        item.bestMatch?.medicalCategoryCode,
        item.bestMatch?.medicalCategoryName,
        item.statusLabel,
        item.sourceSheet
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q));
    });
  }, [items, search, statusFilter, priceFilter]);

  useEffect(() => {
    let mounted = true;
    setCategoriesLoading(true);
    getAllMedicalCategories()
      .then((data) => {
        if (!mounted) return;
        setCategories(Array.isArray(data) ? data.filter((category) => category.active !== false && category.deleted !== true) : []);
      })
      .catch(() => {
        if (mounted) setError('تعذر تحميل التصنيفات الطبية للتعديل اليدوي');
      })
      .finally(() => {
        if (mounted) setCategoriesLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    providersService
      .getSelector()
      .then((data) => setProviders(Array.isArray(data) ? data : data?.content || []))
      .catch(() => setError('تعذر تحميل قائمة مقدمي الخدمة؛ لا يمكن تنفيذ مطابقة V50 الخاصة بالمرفق'));
  }, []);

  useEffect(() => {
    const backendSessionId = searchParams.get('sessionId');
    if (backendSessionId) return;
    const session = loadClassificationSession();
    if (!session?.rawRows?.length) return;

    setSessionInfo(session);
    setFileName(session.fileName || '');
    setRawRows(session.rawRows || []);
    setResult({
      summary: buildSummary(session.items || []),
      items: session.items || []
    });
    setClassificationProgress({
      done: session.done || 0,
      total: session.total || session.rawRows.length
    });
    if (session.status === 'COMPLETED') {
      setSuccess(`تم استرجاع آخر نتيجة محفوظة: ${session.done || 0} خدمة مصنفة.`);
    } else if ((session.done || 0) > 0) {
      setSuccess(
        `تم استرجاع جلسة تصنيف غير مكتملة: ${session.done} من ${session.total || session.rawRows.length}. يمكنك المتابعة من نفس النقطة.`
      );
    }
  }, [searchParams]);

  useEffect(() => {
    const backendSessionId = searchParams.get('sessionId');
    if (!backendSessionId) return;

    let mounted = true;
    setLoading(true);
    setError('');
    medicalDictionaryService
      .getPriceListClassificationSession(backendSessionId)
      .then((session) => {
        if (!mounted) return;
        const mappedItems = mapBackendSessionItems(session);
        setFileName(session.originalFileName || session.sessionName || '');
        setRawRows(
          mappedItems.map((item) => ({
            rowNumber: item.rowNumber,
            sourceSheet: item.sourceSheet,
            serviceCode: item.serviceCode,
            serviceName: item.serviceName,
            price: item.price,
            minPrice: item.minPrice,
            maxPrice: item.maxPrice,
            priceLabel: item.priceLabel
          }))
        );
        setResult({ summary: buildSummary(mappedItems), items: mappedItems });
        setSessionInfo({
          backendSessionId: session.id,
          backendStatus: session.status,
          backendSummary: session.summary,
          fileName: session.originalFileName || session.sessionName || '',
          done: mappedItems.length,
          total: mappedItems.length,
          status: 'COMPLETED',
          savedAtBackend: session.updatedAt
        });
        setClassificationProgress({ done: mappedItems.length, total: mappedItems.length });
        setSuccess(`تم فتح جلسة قائمة الأسعار #${session.id}`);
      })
      .catch((err) => {
        if (mounted) setError(err?.response?.data?.message || 'فشل فتح جلسة قائمة الأسعار');
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [searchParams]);

  const applyManualCategory = (item, categoryId) => {
    applyManualCategoryByKeys([rowKey(item)], categoryId);
  };

  const applyManualCategoryByKeys = (keys, categoryId) => {
    const category = categories.find((entry) => String(entry.id) === String(categoryId));
    const targetKeys = new Set(keys || []);
    setResult((previous) => {
      if (!previous) return previous;
      const nextItems = previous.items.map((row) => {
        if (!targetKeys.has(rowKey(row))) return row;
        if (!category) {
          const { manualCategory, ...rest } = row;
          return rest;
        }
        return {
          ...row,
          manualCategory: {
            medicalCategoryId: category.id,
            medicalCategoryCode: category.code,
            medicalCategoryName: category.name
          }
        };
      });
      return {
        ...previous,
        items: nextItems
      };
    });
  };

  const handleFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    clearClassificationSession();
    setError('');
    setSuccess('');
    setSessionInfo(null);
    setClassificationProgress(null);
    setResult(null);
    setFileName(file.name);

    try {
      const XLSX = await loadXlsx();
      const buffer = await file.arrayBuffer();
      const workbook = XLSX.read(buffer, { type: 'array' });
      const rows = extractRowsFromWorkbook(workbook, XLSX);
      setRawRows(rows);
      saveClassificationSession({
        status: 'READY',
        fileName: file.name,
        rawRows: rows,
        items: [],
        done: 0,
        total: rows.length,
        batchSize: CLASSIFICATION_BATCH_SIZE
      });
      setSessionInfo(loadClassificationSession());
      if (!rows.length) {
        setError('لم أجد خدمات قابلة للتصنيف داخل الملف. تحقق من بنية الأعمدة أو جرّب قالباً أوضح.');
      }
    } catch {
      setError('تعذر قراءة ملف Excel. تأكد من أن الملف xlsx أو xls صالح.');
    }
  };

  const classifyRows = async () => {
    if (!rawRows.length) return;
    if (!selectedProvider?.name) {
      setError('اختر مقدم الخدمة أولاً؛ اسم المرفق جزء أساسي من دقة مطابقة V50.');
      return;
    }
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const persisted = loadClassificationSession();
      const shouldResume = persisted?.rawRows?.length === rawRows.length && (persisted.done || 0) < rawRows.length;
      const allItems = shouldResume ? [...(persisted.items || [])] : [];
      let startIndex = shouldResume ? persisted.done || 0 : 0;

      setClassificationProgress({ done: startIndex, total: rawRows.length });
      saveClassificationSession({
        status: 'RUNNING',
        fileName,
        rawRows,
        items: allItems,
        done: startIndex,
        total: rawRows.length,
        batchSize: CLASSIFICATION_BATCH_SIZE
      });

      for (let index = startIndex; index < rawRows.length; index += CLASSIFICATION_BATCH_SIZE) {
        const chunk = rawRows.slice(index, index + CLASSIFICATION_BATCH_SIZE);
        const response = await medicalDictionaryService.classifyPriceListWithDictionary({
          providerName: selectedProvider.name,
          rows: chunk
        });
        allItems.push(...(response.items || []));
        startIndex = Math.min(index + chunk.length, rawRows.length);

        const nextResult = {
          summary: buildSummary(allItems),
          items: allItems
        };
        setResult(nextResult);
        setClassificationProgress({ done: startIndex, total: rawRows.length });

        saveClassificationSession({
          status: startIndex >= rawRows.length ? 'COMPLETED' : 'RUNNING',
          fileName,
          rawRows,
          items: allItems,
          done: startIndex,
          total: rawRows.length,
          batchSize: CLASSIFICATION_BATCH_SIZE
        });
        setSessionInfo(loadClassificationSession());

        await new Promise((resolve) => setTimeout(resolve, 0));
      }

      setSuccess(`اكتمل التصنيف: ${allItems.length} خدمة. تم حفظ النتائج ويمكن الرجوع لها لاحقاً.`);
    } catch (err) {
      const persisted = loadClassificationSession();
      if (persisted) {
        setSessionInfo(persisted);
        setClassificationProgress({ done: persisted.done || 0, total: persisted.total || rawRows.length });
      }
      setError(err?.response?.data?.message || 'فشل تصنيف قائمة الأسعار بالقاموس');
    } finally {
      setLoading(false);
    }
  };

  const clearCurrentClassificationSession = () => {
    clearClassificationSession();
    setSessionInfo(null);
    setClassificationProgress(null);
    setResult(null);
    setRawRows([]);
    setFileName('');
    setSuccess('تم مسح جلسة التصنيف المحفوظة.');
  };

  const buildBackendSessionPayload = () => ({
    sessionId: sessionInfo?.backendSessionId || null,
    sessionName: fileName ? `تنظيم قائمة أسعار - ${fileName}` : `تنظيم قائمة أسعار - ${new Date().toLocaleDateString('ar-LY')}`,
    originalFileName: fileName || '',
    providerId: selectedProvider?.id || null,
    providerName: selectedProvider?.name || '',
    items: items.map((item) => {
      const effectiveCategory = getEffectiveCategory(item);
      const isManual = Boolean(item.manualCategory);
      return {
        rowNumber: item.rowNumber,
        sourceSheet: item.sourceSheet,
        serviceCode: item.serviceCode || '',
        serviceName: item.serviceName,
        canonicalName: item.bestMatch?.canonicalName || '',
        dictionaryEntryId: item.bestMatch?.entryId || null,
        medicalCategoryId: effectiveCategory?.medicalCategoryId || null,
        medicalCategoryCode: effectiveCategory?.medicalCategoryCode || '',
        medicalCategoryName: effectiveCategory?.medicalCategoryName || '',
        confidence: isManual ? 95 : item.bestMatch?.confidence || 0,
        dictionaryReleaseId: item.dictionaryReleaseId || null,
        dictionaryVersion: item.dictionaryVersion || null,
        dictionaryConceptCode: item.conceptCode || null,
        classificationMethod: item.matchMethod || null,
        classificationReason: item.reason || null,
        classificationExceptionType: item.exceptionType || null,
        classificationEvidenceId: item.evidenceId || null,
        classificationExcludePrecision: Boolean(item.excludeFromPrecision),
        status: isManual ? 'MANUALLY_REVIEWED' : item.status,
        price: item.price ?? getPriceMin(item),
        minPrice: getPriceMin(item),
        maxPrice: getPriceMax(item),
        priceLabel: item.priceLabel || '',
        duplicateName: Boolean(item.duplicateName),
        mergedDuplicate: false,
        mergedSourceCount: 1,
        mergeNotes: item.duplicateName ? 'اسم خدمة مكرر في المصدر' : '',
        manualReviewNote: isManual ? 'تم تعديل التصنيف يدوياً داخل نافذة تنظيم قوائم الأسعار' : ''
      };
    })
  });

  const saveSessionToBackend = async () => {
    if (!items.length) {
      setError('لا توجد نتيجة تصنيف لحفظها.');
      return;
    }

    setSavingSession(true);
    setError('');
    setSuccess('');
    try {
      const saved = await medicalDictionaryService.savePriceListClassificationSession(buildBackendSessionPayload());
      const nextSession = {
        ...(loadClassificationSession() || sessionInfo || {}),
        backendSessionId: saved.id,
        backendStatus: saved.status,
        backendSummary: saved.summary,
        savedAtBackend: new Date().toISOString()
      };
      saveClassificationSession(nextSession);
      setSessionInfo(nextSession);
      setSuccess(`تم حفظ القائمة المصنفة رقم ${saved.id}. الحالة الحالية: ${sessionStatusLabel[saved.status] || 'غير محددة'}.`);
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل حفظ جلسة تنظيم قائمة الأسعار في قاعدة البيانات');
    } finally {
      setSavingSession(false);
    }
  };

  const promoteReviewRowsToDictionarySuggestions = async () => {
    const reviewRows = items.filter((item) => item.status !== 'AUTO_APPROVED');
    if (!reviewRows.length) {
      setSuccess('لا توجد خدمات تحتاج ترحيل للاقتراحات.');
      return;
    }

    setPromoting(true);
    setError('');
    setSuccess('');
    try {
      let created = 0;
      for (const item of reviewRows) {
        const effectiveCategory = getEffectiveCategory(item);
        await medicalDictionaryService.createDictionarySuggestion({
          originalText: item.serviceName,
          suggestedEntryId: item.bestMatch?.entryId || null,
          suggestedCategoryId: effectiveCategory?.medicalCategoryId || null,
          source: 'PRICE_LIST_IMPORT',
          confidence: item.manualCategory ? 90 : item.bestMatch?.confidence || 0,
          sourceReference: `${fileName || 'price-list'} | ${item.sourceSheet || '-'} | row ${item.rowNumber || '-'}`
        });
        created += 1;
      }
      setSuccess(`تم ترحيل ${created} خدمة إلى اقتراحات القاموس للمراجعة، بدون اعتماد تلقائي.`);
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل ترحيل الخدمات إلى اقتراحات القاموس');
    } finally {
      setPromoting(false);
    }
  };

  const approveMatchedRowsAsSynonyms = async () => {
    const synonymRows = items.filter(
      (item) => item.bestMatch?.entryId && item.serviceName && item.serviceName !== item.bestMatch?.canonicalName
    );
    if (!synonymRows.length) {
      setSuccess('لا توجد صفوف مرتبطة باسم موحد يمكن اعتمادها كمرادفات.');
      return;
    }

    setApprovingSynonyms(true);
    setError('');
    setSuccess('');
    let approved = 0;
    let skipped = 0;
    try {
      for (const item of synonymRows) {
        try {
          await medicalDictionaryService.addDictionarySynonym(item.bestMatch.entryId, {
            synonym: item.serviceName,
            synonymType: 'PROVIDER_SPECIFIC',
            language: /[A-Za-z]/.test(item.serviceName) ? 'en' : 'ar'
          });
          approved += 1;
        } catch {
          skipped += 1;
        }
      }
      setSuccess(`تم اعتماد ${approved} مرادف للقاموس. تم تخطي ${skipped} صف غالباً لأنها مرادفات موجودة مسبقاً.`);
    } catch {
      setError('فشل اعتماد المرادفات من نتائج قائمة الأسعار');
    } finally {
      setApprovingSynonyms(false);
    }
  };

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={3}>
        <Box>
          <Typography variant="h2" sx={{ fontWeight: 900 }}>
            تنظيم قوائم الأسعار بالقاموس
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 0.75 }}>
            أداة داخلية لتصنيف الخدمات وترتيبها اعتماداً على القاموس الطبي. لا تعتمد أي قرار مالي ولا تغيّر السقوف أو نسب التغطية.
          </Typography>
        </Box>

        <Alert severity="info" icon={<PsychologyAltIcon />}>
          القاموس يقترح التصنيف والاسم الموحد فقط. محرك التغطية يبقى المسؤول الوحيد عن الحسابات المالية، النسب، السقوف، الرفض، والتحمل.
        </Alert>

        {sessionInfo?.rawRows?.length > 0 && (
          <Alert
            severity={sessionInfo.status === 'COMPLETED' ? 'success' : 'warning'}
            action={
              <Button color="inherit" size="small" onClick={clearCurrentClassificationSession}>
                مسح الجلسة
              </Button>
            }
          >
            جلسة محفوظة: {sessionInfo.done || 0} من {sessionInfo.total || sessionInfo.rawRows.length} خدمة
            {sessionInfo.savedAt ? ` — آخر حفظ ${new Date(sessionInfo.savedAt).toLocaleString('ar-LY')}` : ''}
            {sessionInfo.status === 'COMPLETED' ? ' — إذا كانت النتائج من استخراج قديم غير دقيق، امسح الجلسة وارفع الملف من جديد.' : ''}
          </Alert>
        )}

        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                  1. رفع قائمة الأسعار
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  ارفع ملف Excel خام. سيتم استخراج أسماء الخدمات والأسعار محلياً ثم إرسال النصوص فقط للتصنيف.
                </Typography>
                <Button component="label" variant="contained" startIcon={<CloudUploadIcon />} fullWidth>
                  اختيار ملف Excel
                  <input hidden type="file" accept=".xlsx,.xls" onChange={handleFile} />
                </Button>
                <Button
                  sx={{ mt: 1 }}
                  variant="outlined"
                  startIcon={<FileDownloadIcon />}
                  onClick={() => downloadTemplate(categories)}
                  fullWidth
                >
                  تحميل قالب قياسي
                </Button>
                {fileName && <Chip sx={{ mt: 2 }} label={fileName} variant="outlined" />}
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                  2. تصنيف بالقاموس
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  تم استخراج {rawRows.length} خدمة. سيتم التصنيف على دفعات من {CLASSIFICATION_BATCH_SIZE} خدمة لحماية الأداء.
                </Typography>
                <Button
                  variant="contained"
                  color="secondary"
                  startIcon={loading ? <CircularProgress size={18} /> : <ManageSearchIcon />}
                  disabled={!rawRows.length || !selectedProvider || loading}
                  onClick={classifyRows}
                  fullWidth
                >
                  {(classificationProgress?.done || 0) > 0 && (classificationProgress?.done || 0) < rawRows.length
                    ? 'متابعة التصنيف'
                    : 'تصنيف الخدمات'}
                </Button>
                <Autocomplete
                  sx={{ mt: 1.5 }}
                  options={providers}
                  value={selectedProvider}
                  onChange={(event, value) => setSelectedProvider(value)}
                  getOptionLabel={(option) => option?.name || ''}
                  isOptionEqualToValue={(option, value) => option.id === value.id}
                  renderInput={(params) => <TextField {...params} label="مقدم الخدمة (إلزامي لدقة V50)" />}
                />
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
                  3. تصدير للمراجعة
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  صدّر النتائج كملف مراجعة يحتوي الاسم الأصلي، الاسم الموحد، التصنيف، والثقة.
                </Typography>
                <Button
                  variant="outlined"
                  startIcon={<FileDownloadIcon />}
                  disabled={!items.length}
                  onClick={() => exportRows(items)}
                  fullWidth
                >
                  تصدير Excel
                </Button>
                <Button
                  sx={{ mt: 1 }}
                  variant="contained"
                  color="success"
                  startIcon={<FileDownloadIcon />}
                  disabled={!items.some((item) => item.bestMatch?.medicalCategoryId)}
                  onClick={() => exportProviderContractReadyRows(items, categories, mergeDuplicatesForContracts)}
                  fullWidth
                >
                  تصدير للعقود
                </Button>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {loading && (
          <LinearProgress
            variant={classificationProgress?.total ? 'determinate' : 'indeterminate'}
            value={
              classificationProgress?.total
                ? Math.min(100, Math.round(((classificationProgress.done || 0) / classificationProgress.total) * 100))
                : undefined
            }
          />
        )}
        {classificationProgress && (
          <Typography variant="body2" color="text.secondary">
            التقدم الحقيقي: {classificationProgress.done} من {classificationProgress.total}
            {classificationProgress.total
              ? ` (${Math.round(((classificationProgress.done || 0) / classificationProgress.total) * 100)}%)`
              : ''}
          </Typography>
        )}
        {error && <Alert severity="error">{error}</Alert>}
        {success && <Alert severity="success">{success}</Alert>}

        {result && (
          <Card>
            <CardContent>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={2}
                alignItems={{ xs: 'stretch', md: 'center' }}
                justifyContent="space-between"
              >
                <Box>
                  <Typography variant="h4" sx={{ fontWeight: 900 }}>
                    نتيجة التصنيف
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    هذه النتائج للمراجعة والتنظيم فقط، وليست اعتماداً مالياً.
                  </Typography>
                </Box>
              </Stack>

              <Grid container spacing={1.25} sx={{ mt: 2 }}>
                {[
                  { label: 'الإجمالي', value: result.summary?.total || 0, color: 'text.primary' },
                  { label: 'ثقة عالية', value: result.summary?.highConfidence || 0, color: 'success.main' },
                  { label: 'تحتاج مراجعة', value: result.summary?.needsReview || 0, color: 'warning.main' },
                  { label: 'غير معروف', value: result.summary?.unknown || 0, color: 'error.main' },
                  { label: 'مكرر', value: result.summary?.duplicateNames || 0, color: 'info.main' },
                  { label: 'أسعار بمجال', value: result.summary?.rangedPrices || 0, color: 'warning.main' },
                  { label: 'جاهز للعقود', value: contractDisplayRows.length, color: 'success.main' },
                  { label: 'بعد الدمج', value: mergedContractRows.length, color: 'success.main' },
                  { label: 'مراجع يدوياً', value: manualReviewedCount, color: 'secondary.main' },
                  { label: 'جاهز كمرادف', value: synonymReadyCount, color: 'primary.main' }
                ].map((stat) => (
                  <Grid key={stat.label} item xs={6} sm={4} md={2.4}>
                    <Card variant="outlined" sx={{ bgcolor: 'grey.50', height: '100%' }}>
                      <CardContent sx={{ py: 1.25, '&:last-child': { pb: 1.25 } }}>
                        <Typography variant="caption" color="text.secondary">
                          {stat.label}
                        </Typography>
                        <Typography variant="h5" sx={{ fontWeight: 900, color: stat.color }}>
                          {stat.value}
                        </Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>

              <Divider sx={{ my: 2.5 }} />

              <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1.25} sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
                <TextField
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="بحث: كود المرفق، اسم خدمة المرفق، الاسم الموحد، التصنيف، الحالة..."
                  sx={{ flex: '1 1 320px', minWidth: 260 }}
                />
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={mergeDuplicatesForContracts}
                      onChange={(event) => setMergeDuplicatesForContracts(event.target.checked)}
                      color="success"
                    />
                  }
                  label="دمج التكرارات"
                  sx={{ minWidth: 170, m: 0 }}
                />
                <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} sx={{ minWidth: 190 }}>
                  <MenuItem value="ALL">كل النتائج</MenuItem>
                  {Object.entries(v50StatusLabel)
                    .slice(0, 6)
                    .map(([value, label]) => (
                      <MenuItem key={value} value={value}>
                        {label}
                      </MenuItem>
                    ))}
                </Select>
                <Select value={priceFilter} onChange={(e) => setPriceFilter(e.target.value)} sx={{ minWidth: 190 }}>
                  <MenuItem value="ALL">كل الأسعار</MenuItem>
                  <MenuItem value="RANGE">أسعار بمجال</MenuItem>
                  <MenuItem value="SINGLE">سعر مفرد</MenuItem>
                  <MenuItem value="MISSING">بدون سعر</MenuItem>
                </Select>
                <Button
                  variant="outlined"
                  startIcon={<FileDownloadIcon />}
                  onClick={() => downloadTemplate(categories)}
                  sx={{ minWidth: 130 }}
                >
                  قالب Excel
                </Button>
                <Button
                  variant="contained"
                  color="secondary"
                  startIcon={savingSession ? <CircularProgress size={18} /> : <PlaylistAddCheckIcon />}
                  disabled={!items.length || savingSession}
                  onClick={saveSessionToBackend}
                >
                  حفظ الجلسة
                </Button>
                <Button
                  variant="contained"
                  color="success"
                  startIcon={<FileDownloadIcon />}
                  disabled={!items.some((item) => item.bestMatch?.medicalCategoryId)}
                  onClick={() => exportProviderContractReadyRows(items, categories, mergeDuplicatesForContracts)}
                >
                  تصدير للعقود
                </Button>
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={approvingSynonyms ? <CircularProgress size={18} /> : <LibraryAddCheckIcon />}
                  disabled={!synonymReadyCount || approvingSynonyms}
                  onClick={approveMatchedRowsAsSynonyms}
                >
                  اعتماد كمرادفات
                </Button>
                <Button
                  variant="contained"
                  color="warning"
                  startIcon={promoting ? <CircularProgress size={18} /> : <PlaylistAddCheckIcon />}
                  disabled={!items.some((item) => item.status !== 'AUTO_APPROVED') || promoting}
                  onClick={promoteReviewRowsToDictionarySuggestions}
                >
                  ترحيل للمراجعة
                </Button>
              </Stack>

              <TableContainer sx={{ maxHeight: 620 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>#</TableCell>
                      <TableCell>خدمة المرفق</TableCell>
                      <TableCell>السعر</TableCell>
                      <TableCell>الحالة</TableCell>
                      <TableCell>الثقة</TableCell>
                      <TableCell>الاسم الموحد</TableCell>
                      <TableCell>التصنيف</TableCell>
                      <TableCell>تعديل التصنيف</TableCell>
                      <TableCell>ملاحظات</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {mergeDuplicatesForContracts
                      ? filteredMergedContractRows.map((row, index) => (
                          <TableRow key={`${row.service_code || '-'}-${row.service_name}-${row.medical_category_code}-${index}`} hover>
                            <TableCell>{index + 1}</TableCell>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography sx={{ fontWeight: 700 }}>{row.service_name}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.service_code ? `كود المرفق: ${row.service_code} • ` : ''}
                                  {row.sourceKeys?.length > 1 ? `مدموج من ${row.sourceKeys.length} صفوف` : 'صف واحد'}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{row.contract_price || '-'}</TableCell>
                            <TableCell>
                              <Stack direction="row" spacing={0.5} flexWrap="wrap">
                                <Chip
                                  size="small"
                                  color={statusColor[row.display_status] || 'default'}
                                  label={row.display_status_label || '-'}
                                />
                                {row.sourceKeys?.length > 1 && <Chip size="small" color="info" variant="outlined" label="مدموج" />}
                              </Stack>
                            </TableCell>
                            <TableCell>{row.confidence ?? '-'}</TableCell>
                            <TableCell>{row.canonical_service_name || '-'}</TableCell>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography>{row.medical_category_name || '-'}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {row.medical_category_code || '-'}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell sx={{ minWidth: 240 }}>
                              <Autocomplete
                                size="small"
                                options={categories}
                                disabled={categoriesLoading}
                                value={categories.find((category) => category.code === row.medical_category_code) || null}
                                onChange={(event, category) => applyManualCategoryByKeys(row.sourceKeys || [], category?.id || '')}
                                getOptionLabel={(category) =>
                                  category ? `${category.nameAr || category.name || ''}${category.code ? ` (${category.code})` : ''}` : ''
                                }
                                isOptionEqualToValue={(option, value) => option.id === value.id}
                                filterOptions={(options, state) => {
                                  const query = state.inputValue.trim().toLowerCase();
                                  if (!query) return options;
                                  return options.filter((category) =>
                                    [category.code, category.name, category.nameAr, category.nameEn]
                                      .filter(Boolean)
                                      .some((value) => String(value).toLowerCase().includes(query))
                                  );
                                }}
                                renderInput={(params) => <TextField {...params} placeholder="ابحث في التصنيفات..." />}
                              />
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption" color="text.secondary">
                                {row.notes || '-'}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        ))
                      : filteredItems.map((item, index) => (
                          <TableRow key={`${item.sourceSheet}-${item.rowNumber}-${index}`} hover>
                            <TableCell>{item.rowNumber}</TableCell>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography sx={{ fontWeight: 700 }}>{item.serviceName}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {item.serviceCode ? `كود المرفق: ${item.serviceCode} • ` : ''}
                                  {item.sourceSheet}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{item.priceLabel || formatContractPrice(getPriceMin(item), getPriceMax(item)) || '-'}</TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                color={item.manualCategory ? 'secondary' : statusColor[item.status] || 'default'}
                                label={getEffectiveStatusLabel(item)}
                              />
                            </TableCell>
                            <TableCell>{item.bestMatch?.confidence ?? '-'}</TableCell>
                            <TableCell>{item.bestMatch?.canonicalName ?? '-'}</TableCell>
                            <TableCell>
                              {getEffectiveCategory(item) ? (
                                <Stack spacing={0.25}>
                                  <Typography>{getEffectiveCategory(item).medicalCategoryName}</Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {getEffectiveCategory(item).medicalCategoryCode}
                                  </Typography>
                                </Stack>
                              ) : (
                                '-'
                              )}
                            </TableCell>
                            <TableCell sx={{ minWidth: 240 }}>
                              <Autocomplete
                                size="small"
                                options={categories}
                                disabled={categoriesLoading}
                                value={categories.find((category) => category.id === item.manualCategory?.medicalCategoryId) || null}
                                onChange={(event, category) => applyManualCategory(item, category?.id || '')}
                                getOptionLabel={(category) =>
                                  category ? `${category.nameAr || category.name || ''}${category.code ? ` (${category.code})` : ''}` : ''
                                }
                                isOptionEqualToValue={(option, value) => option.id === value.id}
                                filterOptions={(options, state) => {
                                  const query = state.inputValue.trim().toLowerCase();
                                  if (!query) return options;
                                  return options.filter((category) =>
                                    [category.code, category.name, category.nameAr, category.nameEn]
                                      .filter(Boolean)
                                      .some((value) => String(value).toLowerCase().includes(query))
                                  );
                                }}
                                renderInput={(params) => <TextField {...params} placeholder="ابحث في التصنيفات..." />}
                              />
                            </TableCell>
                            <TableCell>
                              <Stack spacing={0.25}>
                                {item.duplicateName && <Chip size="small" color="info" label="اسم مكرر" />}
                                <Typography variant="caption" color="text.secondary">
                                  {item.dictionaryVersion ? `${item.dictionaryVersion} • ${item.matchMethod || '-'}` : item.reason || '-'}
                                </Typography>
                                {item.reason && <Typography variant="caption">{item.reason}</Typography>}
                              </Stack>
                            </TableCell>
                          </TableRow>
                        ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}
      </Stack>
    </Box>
  );
}
