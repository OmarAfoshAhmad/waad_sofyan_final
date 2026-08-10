import { useDeferredValue, useEffect, useMemo, useState } from 'react';
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
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
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
  TablePagination,
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
import { useQueryClient } from '@tanstack/react-query';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';
import providersService from 'services/api/providers.service';
import { getActiveContractByProvider } from 'services/api/provider-contracts.service';
import { useSnackbar } from 'notistack';

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

const isContractEligible = (item) => {
  if (!getEffectiveCategory(item)?.medicalCategoryId || getPriceMin(item) == null) return false;
  if (item.status === 'POSTED_TO_CONTRACT' || item.postedPricingItemId) return true;
  if (item.manualCategory) return true;
  return (
    item.status === 'AUTO_APPROVED' && Boolean(item.dictionaryReleaseId && item.dictionaryVersion && item.matchMethod && item.evidenceId)
  );
};

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
  const acceptedItems = items.filter(isContractEligible);
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
        statuses: new Set(),
        postedCount: 0
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
    if (item.postedPricingItemId || item.status === 'POSTED_TO_CONTRACT') group.postedCount += 1;
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
      posting_status: group.postedCount === group.sourceKeys.length ? 'POSTED' : group.postedCount > 0 ? 'PARTIAL' : 'UNPOSTED',
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
  items.filter(isContractEligible).map((item) => {
    const effectiveCategory = getEffectiveCategory(item);
    const min = getPriceMin(item);
    const max = getPriceMax(item);
    const canonicalServiceName = getCanonicalExportName(item);
    return {
      sourceKeys: [rowKey(item)],
      rawStatuses: item.status ? [item.status] : [],
      display_status: item.status || 'REVIEW_REQUIRED',
      display_status_label: getEffectiveStatusLabel(item),
      posting_status: item.postedPricingItemId || item.status === 'POSTED_TO_CONTRACT' ? 'POSTED' : 'UNPOSTED',
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
    id: item.id,
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
    postedPricingItemId: item.postedPricingItemId,
    postedAt: item.postedAt,
    duplicateName: Boolean(item.duplicateName),
    manualCategory:
      item.status === 'MANUALLY_REVIEWED' && item.medicalCategoryId
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
  const { enqueueSnackbar } = useSnackbar();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [fileName, setFileName] = useState('');
  const [rawRows, setRawRows] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [classificationProgress, setClassificationProgress] = useState(null);
  const [promoting, setPromoting] = useState(false);
  const [approvingSynonyms, setApprovingSynonyms] = useState(false);
  const [savingSession, setSavingSession] = useState(false);
  const [postingContract, setPostingContract] = useState(false);
  const [postConfirm, setPostConfirm] = useState({ open: false, data: null, message: '' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [priceFilter, setPriceFilter] = useState('ALL');
  const [postingStatusFilter, setPostingStatusFilter] = useState('ALL');
  const [selectedSourceKeys, setSelectedSourceKeys] = useState([]);
  const [mergeDuplicatesForContracts, setMergeDuplicatesForContracts] = useState(true);
  const [categories, setCategories] = useState([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [sessionInfo, setSessionInfo] = useState(null);
  const [providers, setProviders] = useState([]);
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);

  useEffect(() => {
    if (success) enqueueSnackbar(success, { variant: 'success', preventDuplicate: true });
  }, [success, enqueueSnackbar]);

  useEffect(() => {
    if (error) enqueueSnackbar(error, { variant: 'error', preventDuplicate: true });
  }, [error, enqueueSnackbar]);

  const items = useMemo(() => result?.items || [], [result?.items]);
  const selectedProviderId = useMemo(() => {
    if (selectedProvider?.id) return selectedProvider.id;
    if (selectedProvider?.providerId) return selectedProvider.providerId;
    const selectedName = normalizeText(selectedProvider?.name || sessionInfo?.providerName).toLowerCase();
    if (!selectedName) return null;
    return providers.find((provider) => normalizeText(provider?.name).toLowerCase() === selectedName)?.id || null;
  }, [selectedProvider, sessionInfo?.providerName, providers]);
  const deferredSearch = useDeferredValue(search);
  const itemStats = useMemo(
    () =>
      items.reduce(
        (stats, item) => {
          if (item.manualCategory) stats.manualReviewed += 1;
          if (item.bestMatch?.entryId && item.serviceName && item.serviceName !== item.bestMatch?.canonicalName) stats.synonymReady += 1;
          return stats;
        },
        { manualReviewed: 0, synonymReady: 0 }
      ),
    [items]
  );
  const manualReviewedCount = itemStats.manualReviewed;
  const synonymReadyCount = itemStats.synonymReady;
  const mergedContractRows = useMemo(() => buildContractReadyRows(items), [items]);
  const unmergedContractRows = useMemo(() => buildContractRowsWithoutMerge(items), [items]);
  const contractDisplayRows = mergeDuplicatesForContracts ? mergedContractRows : unmergedContractRows;
  const filteredMergedContractRows = useMemo(() => {
    const q = deferredSearch.trim().toLowerCase();
    return contractDisplayRows.filter((row) => {
      if (statusFilter !== 'ALL' && row.display_status !== statusFilter) return false;
      if (postingStatusFilter !== 'ALL' && row.posting_status !== postingStatusFilter) return false;
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
  }, [contractDisplayRows, deferredSearch, statusFilter, priceFilter, postingStatusFilter]);
  const filteredItems = useMemo(() => {
    const q = deferredSearch.trim().toLowerCase();
    return items.filter((item) => {
      if (statusFilter !== 'ALL' && item.status !== statusFilter) return false;
      const postingStatus = item.postedPricingItemId || item.status === 'POSTED_TO_CONTRACT' ? 'POSTED' : 'UNPOSTED';
      if (postingStatusFilter !== 'ALL' && postingStatus !== postingStatusFilter) return false;
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
  }, [items, deferredSearch, statusFilter, priceFilter, postingStatusFilter]);
  const visibleRows = mergeDuplicatesForContracts ? filteredMergedContractRows : filteredItems;
  const pagedRows = useMemo(
    () => visibleRows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage),
    [visibleRows, page, rowsPerPage]
  );

  useEffect(() => {
    setPage(0);
  }, [deferredSearch, statusFilter, priceFilter, postingStatusFilter, mergeDuplicatesForContracts]);

  const visibleSourceKeys = useMemo(
    () => Array.from(new Set(visibleRows.flatMap((row) => row.sourceKeys || [rowKey(row)]))),
    [visibleRows]
  );
  const selectedKeySet = useMemo(() => new Set(selectedSourceKeys), [selectedSourceKeys]);
  const selectedDisplayRows = useMemo(
    () =>
      contractDisplayRows.filter((row) => {
        const keys = row.sourceKeys || [rowKey(row)];
        return keys.length > 0 && keys.every((key) => selectedKeySet.has(key));
      }),
    [contractDisplayRows, selectedKeySet]
  );
  const selectedDisplayRowCount = selectedDisplayRows.length;
  const allVisibleSelected = visibleSourceKeys.length > 0 && visibleSourceKeys.every((key) => selectedKeySet.has(key));
  const someVisibleSelected = visibleSourceKeys.some((key) => selectedKeySet.has(key));

  const toggleAllVisibleRows = () => {
    setSelectedSourceKeys((current) => {
      const currentSet = new Set(current);
      if (allVisibleSelected) visibleSourceKeys.forEach((key) => currentSet.delete(key));
      else visibleSourceKeys.forEach((key) => currentSet.add(key));
      return Array.from(currentSet);
    });
  };

  const toggleRowSelection = (keys) => {
    const normalizedKeys = keys || [];
    setSelectedSourceKeys((current) => {
      const currentSet = new Set(current);
      const selected = normalizedKeys.length > 0 && normalizedKeys.every((key) => currentSet.has(key));
      normalizedKeys.forEach((key) => (selected ? currentSet.delete(key) : currentSet.add(key)));
      return Array.from(currentSet);
    });
  };

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
      .then((data) => {
        const options = Array.isArray(data) ? data : data?.content || [];
        setProviders(options);
        const selectedName = normalizeText(selectedProvider?.name || sessionInfo?.providerName).toLowerCase();
        if (!selectedProvider?.id && selectedName) {
          const restored = options.find((provider) => normalizeText(provider?.name).toLowerCase() === selectedName);
          if (restored) setSelectedProvider(restored);
        }
      })
      .catch(() => setError('تعذر تحميل قائمة مقدمي الخدمة؛ لا يمكن تنفيذ مطابقة V50 الخاصة بالمرفق'));
  }, [selectedProvider?.id, selectedProvider?.name, sessionInfo?.providerName]);

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
    if (session.providerId || session.providerName) {
      setSelectedProvider({ id: session.providerId || null, name: session.providerName || '' });
    }
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
        if (session.providerId || session.providerName) {
          setSelectedProvider({ id: session.providerId || null, name: session.providerName || '' });
        }
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
        providerId: selectedProvider.id,
        providerName: selectedProvider.name,
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
          providerId: selectedProvider.id,
          providerName: selectedProvider.name,
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

  const buildBackendSessionPayload = (itemsToSave = items, options = {}) => ({
    sessionId: options.sessionId === undefined ? sessionInfo?.backendSessionId || null : options.sessionId,
    sessionName: fileName
      ? `${options.selectedOnly ? 'ترحيل محدد' : 'تنظيم قائمة أسعار'} - ${fileName}`
      : `${options.selectedOnly ? 'ترحيل محدد' : 'تنظيم قائمة أسعار'} - ${new Date().toLocaleDateString('ar-LY')}`,
    originalFileName: fileName || '',
    providerId: selectedProviderId,
    providerName: selectedProvider?.name || '',
    items: itemsToSave.map((item) => {
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
      return saved;
    } catch (err) {
      setError(err?.response?.data?.message || 'فشل حفظ جلسة تنظيم قائمة الأسعار في قاعدة البيانات');
    } finally {
      setSavingSession(false);
    }
    return null;
  };

  const postApprovedRowsToSelectedProviderContract = async () => {
    if (!selectedProviderId) {
      setError('تعذر تحديد هوية مقدم الخدمة من الجلسة القديمة. أعد اختياره من حقل مقدم الخدمة ثم حاول مجدداً.');
      return;
    }
    if (!selectedSourceKeys.length) {
      setError('حدد كل الخدمات المطلوبة أو اختر خدمة واحدة على الأقل قبل الترحيل.');
      return;
    }

    setPostingContract(true);
    setError('');
    setSuccess('');
    try {
      const itemsBySourceKey = new Map(items.map((item) => [rowKey(item), item]));
      const selectedItems = mergeDuplicatesForContracts
        ? selectedDisplayRows
            .map((row) => {
              const representative = (row.sourceKeys || []).map((key) => itemsBySourceKey.get(key)).find(Boolean);
              if (!representative) return null;
              const priceRange = parsePriceRange(row.contract_price);
              return {
                ...representative,
                serviceCode: row.service_code || representative.serviceCode,
                serviceName: row.service_name || representative.serviceName,
                price: priceRange.min,
                minPrice: priceRange.min,
                maxPrice: priceRange.max,
                priceLabel: row.contract_price,
                duplicateName: (row.sourceKeys || []).length > 1
              };
            })
            .filter(Boolean)
        : items.filter((item) => selectedKeySet.has(rowKey(item)));
      if (!selectedItems.length) throw new Error('لا توجد خدمات محددة قابلة للحفظ والترحيل.');
      const saved = await medicalDictionaryService.savePriceListClassificationSession(
        buildBackendSessionPayload(selectedItems, { sessionId: null, selectedOnly: true })
      );
      const selectedItemIds = (saved.items || [])
        .filter((item) => selectedKeySet.has(rowKey(item)))
        .map((item) => item.id)
        .filter(Boolean);
      if (!selectedItemIds.length || selectedItemIds.length !== selectedItems.length) {
        throw new Error('تعذر ربط بعض الخدمات المحددة بالجلسة المحفوظة؛ أعد تحديدها ثم حاول مجدداً.');
      }
      const contract = await getActiveContractByProvider(selectedProviderId);
      if (!contract?.id) throw new Error('لا يوجد عقد نشط لمقدم الخدمة المختار. أنشئ العقد أو فعّله أولاً.');
      // A classified base price list belongs to the contract period, not to the
      // upload day; otherwise legitimate backdated claims inside the contract
      // become unpriceable.
      const effectiveFrom = contract.startDate;
      if (contract.endDate && effectiveFrom > contract.endDate) {
        throw new Error('انتهت مدة العقد النشط ولا يمكن إضافة أسعار جديدة إليه.');
      }

      const diff = await medicalDictionaryService.diffPriceListClassificationSessionWithContract(saved.id, {
        contractId: contract.id,
        effectiveFrom,
        replaceEffectivePrices: false,
        onlyReviewedItems: true,
        itemIds: selectedItemIds
      });
      const accepted = (diff.createCount || 0) + (diff.updateCount || 0) + (diff.identicalCount || 0);
      const message =
        `العقد: ${contract.contractCode || contract.id}\n` +
        `قابل للترحيل/مطابق: ${accepted}\n` +
        `جديد: ${diff.createCount || 0}، تحديث: ${diff.updateCount || 0}، مطابق: ${diff.identicalCount || 0}\n` +
        `مرفوض أو يحتاج مراجعة: ${diff.rejectedCount || 0}\n\n` +
        'لن تُستبدل أسعار فعالة متعارضة تلقائياً. هل تريد المتابعة؟';
      setPostConfirm({
        open: true,
        message,
        data: { savedId: saved.id, selectedItemIds, contract, effectiveFrom }
      });
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'فشل ترحيل الخدمات المعتمدة إلى عقد مقدم الخدمة');
    } finally {
      setPostingContract(false);
    }
  };

  const executeConfirmedContractPost = async () => {
    const data = postConfirm.data;
    if (!data) return;
    setPostConfirm({ open: false, data: null, message: '' });
    setPostingContract(true);
    setError('');
    setSuccess('');
    try {
      const posted = await medicalDictionaryService.postPriceListClassificationSessionToContract(data.savedId, {
        contractId: data.contract.id,
        effectiveFrom: data.effectiveFrom,
        replaceEffectivePrices: false,
        onlyReviewedItems: true,
        itemIds: data.selectedItemIds
      });
      const changed = (posted.created || 0) + (posted.updated || 0);
      if (changed === 0 && (posted.skipped || 0) > 0 && (posted.rejected || 0) === 0) {
        setSuccess(
          `لم تُضف خدمات جديدة: الخدمات المحددة موجودة ومطابقة بالفعل في العقد ${data.contract.contractCode || data.contract.id}.`
        );
      } else {
        setSuccess(
          `اكتمل الترحيل إلى العقد ${data.contract.contractCode || data.contract.id}: أُنشئ ${posted.created || 0}، حُدّث ${
            posted.updated || 0
          }، موجود مسبقاً ${posted.skipped || 0}، ورُفض ${posted.rejected || 0}.`
        );
      }
      setSessionInfo((current) => ({
        ...(current || {}),
        backendSessionId: data.savedId,
        backendStatus: posted.session?.status
      }));
      await queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      await queryClient.invalidateQueries({ queryKey: ['provider-contract'] });
      setSelectedSourceKeys([]);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'فشل ترحيل الخدمات المعتمدة إلى عقد مقدم الخدمة');
    } finally {
      setPostingContract(false);
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

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(3, minmax(0, 1fr))' }, gap: 2 }}>
          <Box>
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
          </Box>

          <Box>
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
          </Box>

          <Box>
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
          </Box>
        </Box>

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
                  { label: 'تكرار حرفي', value: result.summary?.duplicateNames || 0, color: 'info.main' },
                  { label: 'أسعار بمجال', value: result.summary?.rangedPrices || 0, color: 'warning.main' },
                  { label: 'جاهز قبل الدمج', value: unmergedContractRows.length, color: 'success.main' },
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
                <Select value={postingStatusFilter} onChange={(e) => setPostingStatusFilter(e.target.value)} sx={{ minWidth: 190 }}>
                  <MenuItem value="ALL">كل حالات الترحيل</MenuItem>
                  <MenuItem value="UNPOSTED">غير مرحلة</MenuItem>
                  <MenuItem value="POSTED">مرحلة للعقد</MenuItem>
                  {mergeDuplicatesForContracts && <MenuItem value="PARTIAL">مرحلة جزئياً</MenuItem>}
                </Select>
                <Chip
                  color="info"
                  variant="outlined"
                  label={`النتائج المعروضة: ${visibleRows.length} من ${mergeDuplicatesForContracts ? mergedContractRows.length : items.length}`}
                  sx={{ height: 40, px: 1, fontWeight: 800 }}
                />
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
                  color="success"
                  startIcon={postingContract ? <CircularProgress size={18} color="inherit" /> : <PlaylistAddCheckIcon />}
                  disabled={!selectedDisplayRowCount || postingContract || savingSession}
                  onClick={postApprovedRowsToSelectedProviderContract}
                >
                  ترحيل المحدد للعقد ({selectedDisplayRowCount})
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
                      <TableCell padding="checkbox">
                        <Checkbox
                          checked={allVisibleSelected}
                          indeterminate={!allVisibleSelected && someVisibleSelected}
                          onChange={toggleAllVisibleRows}
                          inputProps={{ 'aria-label': 'تحديد كل الخدمات المعروضة' }}
                        />
                      </TableCell>
                      <TableCell>#</TableCell>
                      <TableCell>خدمة المرفق</TableCell>
                      <TableCell>السعر</TableCell>
                      <TableCell>الحالة</TableCell>
                      <TableCell>حالة الترحيل</TableCell>
                      <TableCell>الثقة</TableCell>
                      <TableCell>الاسم الموحد</TableCell>
                      <TableCell>التصنيف</TableCell>
                      <TableCell>تعديل التصنيف</TableCell>
                      <TableCell>ملاحظات</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {mergeDuplicatesForContracts
                      ? pagedRows.map((row, index) => (
                          <TableRow key={`${row.service_code || '-'}-${row.service_name}-${row.medical_category_code}-${index}`} hover>
                            <TableCell padding="checkbox">
                              <Checkbox
                                checked={
                                  (row.sourceKeys || []).length > 0 && (row.sourceKeys || []).every((key) => selectedKeySet.has(key))
                                }
                                onChange={() => toggleRowSelection(row.sourceKeys || [])}
                              />
                            </TableCell>
                            <TableCell>{page * rowsPerPage + index + 1}</TableCell>
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
                            <TableCell>
                              <Chip
                                size="small"
                                color={
                                  row.posting_status === 'POSTED' ? 'success' : row.posting_status === 'PARTIAL' ? 'warning' : 'default'
                                }
                                label={
                                  row.posting_status === 'POSTED'
                                    ? 'مرحلة للعقد'
                                    : row.posting_status === 'PARTIAL'
                                      ? 'مرحلة جزئياً'
                                      : 'غير مرحلة'
                                }
                              />
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
                      : pagedRows.map((item, index) => (
                          <TableRow key={`${item.sourceSheet}-${item.rowNumber}-${index}`} hover>
                            <TableCell padding="checkbox">
                              <Checkbox checked={selectedKeySet.has(rowKey(item))} onChange={() => toggleRowSelection([rowKey(item)])} />
                            </TableCell>
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
                            <TableCell>
                              <Chip
                                size="small"
                                color={item.postedPricingItemId || item.status === 'POSTED_TO_CONTRACT' ? 'success' : 'default'}
                                label={item.postedPricingItemId || item.status === 'POSTED_TO_CONTRACT' ? 'مرحلة للعقد' : 'غير مرحلة'}
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
              <TablePagination
                component="div"
                count={visibleRows.length}
                page={page}
                onPageChange={(event, nextPage) => setPage(nextPage)}
                rowsPerPage={rowsPerPage}
                onRowsPerPageChange={(event) => {
                  setRowsPerPage(Number(event.target.value));
                  setPage(0);
                }}
                rowsPerPageOptions={[10, 25, 50, 100]}
                labelRowsPerPage="عدد الصفوف"
                labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count}`}
              />
            </CardContent>
          </Card>
        )}
      </Stack>
      <Dialog
        open={postConfirm.open}
        onClose={() => !postingContract && setPostConfirm({ open: false, data: null, message: '' })}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>تأكيد ترحيل الخدمات إلى العقد</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-line' }}>{postConfirm.message}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPostConfirm({ open: false, data: null, message: '' })} disabled={postingContract}>
            إلغاء
          </Button>
          <Button variant="contained" color="success" onClick={executeConfirmedContractPost} disabled={postingContract}>
            تأكيد الترحيل
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
