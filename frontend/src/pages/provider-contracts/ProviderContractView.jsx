/**
 * Provider Contract View Page
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Displays detailed view of a single provider contract including:
 * - Contract summary (code, status, dates)
 * - Provider information
 * - Pricing model and discount settings
 * - Pricing items table with search
 * - Lifecycle actions (activate, suspend, terminate)
 *
 * Uses REAL Backend API via provider-contracts.service.js
 *
 * Route: /provider-contracts/:id
 * @version 2.1.0
 * @lastUpdated 2024-05-22
 */

import { useState, useMemo, useCallback, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  TextField,
  InputAdornment,
  Tooltip,
  Typography,
  Autocomplete,
  Alert
} from '@mui/material';
import {
  ArrowBack as BackIcon,
  Business as ProviderIcon,
  CalendarToday as CalendarIcon,
  Description as ContractIcon,
  Edit as EditIcon,
  Info as InfoIcon,
  LocalOffer as PriceIcon,
  Notes as NotesIcon,
  Search as SearchIcon,
  CheckCircle as ActivateIcon,
  PauseCircle as SuspendIcon,
  Cancel as TerminateIcon,
  Refresh as RefreshIcon,
  Delete as DeleteIcon,
  Add as AddIcon
  , Clear as ClearIcon
  , CloudUpload as CloudUploadIcon
  , Download as DownloadIcon
  , InsertDriveFile as FileIcon
  , ExpandMore as ExpandMoreIcon
  , ExpandLess as ExpandLessIcon
} from '@mui/icons-material';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';

// API Service
import {
  getProviderContractById,
  getContractPricingItems,
  activateContract,
  suspendContract,
  terminateContract,
  uploadContractPricingExcel,
  downloadPricingTemplate,
  addPricingItem,
  updatePricingItem,
  deletePricingItem,
  deleteAllPricingItems,
  CONTRACT_STATUS,
  CONTRACT_STATUS_CONFIG,
  PRICING_MODEL_CONFIG
} from 'services/api/provider-contracts.service';
import { getAllMedicalCategories, getMedicalServicesByCategory } from 'services/api/medical-categories.service';

// Snackbar
import { useSnackbar } from 'notistack';

// ═══════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Format date for display
 */
const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  try {
    return new Date(dateStr).toLocaleDateString('en-GB', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  } catch {
    return dateStr;
  }
};

/**
 * Format currency
 */
const formatCurrency = (value) => {
  if (value === null || value === undefined) return '-';
  return (
    new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value) + ' د.ل'
  );
};

const getServiceCode = (item) => item?.medicalService?.code || item?.serviceCode || item?.service?.code || '-';
const getServiceNameAr = (item) => item?.medicalService?.nameAr || item?.serviceNameAr || item?.serviceName || item?.medicalService?.name || item?.service?.name || '-';
const getServiceNameEn = (item) => item?.medicalService?.nameEn || item?.serviceNameEn || item?.medicalService?.name || item?.service?.name || '-';
const getServiceDisplay = (item) => ({
  code: getServiceCode(item),
  nameAr: getServiceNameAr(item),
  nameEn: getServiceNameEn(item)
});

/**
 * Get category hierarchy names
 */
const getCategoryHierarchy = (item, categoriesList = []) => {
  // Try to find category ID from various possible fields
  let catId =
    item?.medicalCategoryId ||
    item?.categoryId ||
    item?.medicalCategory?.id ||
    item?.effectiveCategory?.id ||
    item?.category?.id ||
    item?.medicalService?.categoryId ||
    item?.medicalService?.category?.id;

  // Fallback: If no ID but we have a name, try to find it in the list
  if (!catId && item?.categoryName && Array.isArray(categoriesList)) {
    const matched = categoriesList.find(c =>
      c.nameAr === item.categoryName ||
      c.name === item.categoryName ||
      (item.categoryName.includes('(') && item.categoryName.split('(')[0].trim() === (c.nameAr || c.name))
    );
    if (matched) {
      catId = matched.id;
    }
  }

  if (catId && Array.isArray(categoriesList)) {
    const categoryEntity = categoriesList.find((c) => String(c.id) === String(catId));
    if (categoryEntity) {
      if (categoryEntity.parentId) {
        const parent = categoriesList.find((p) => String(p.id) === String(categoryEntity.parentId));
        return {
          main: parent ? parent.nameAr || parent.name : 'أخرى',
          sub: categoryEntity.nameAr || categoryEntity.name,
          mainCode: parent?.code || '-',
          subCode: categoryEntity.code || '-'
        };
      }
      return {
        main: categoryEntity.nameAr || categoryEntity.name,
        sub: '-',
        mainCode: categoryEntity.code || '-',
        subCode: '-'
      };
    }
  }

  // Fallback for imported items or if lookup fails
  let fallbackMain = item?.categoryName || item?.mainCategoryName || '-';
  let fallbackSub = item?.subCategoryName || '-';

  if (item?.categoryName?.includes(' > ')) {
    const parts = item.categoryName.split(' > ');
    fallbackMain = parts[0];
    fallbackSub = parts[1] || '-';
  }

  return {
    main: fallbackMain,
    sub: fallbackSub,
    mainCode: '-',
    subCode: '-'
  };
};

const getCategoryObject = (item) => item?.medicalCategory || item?.effectiveCategory || item?.category || null;

// ═══════════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Info Row - displays label/value pairs
 */
const InfoRow = ({ label, value, valueColor, icon: Icon }) => (
  <Stack direction="row" spacing={1} alignItems="center" sx={{ py: 0.5 }}>
    {Icon && <Icon sx={{ fontSize: '1.125rem', color: 'text.secondary' }} />}
    <Typography variant="caption" color="text.secondary" sx={{ minWidth: '5.0rem' }}>
      {label}:
    </Typography>
    <Typography variant="body2" fontWeight={600} color={valueColor || 'text.primary'} noWrap>
      {value}
    </Typography>
  </Stack>
);

/**
 * Tab Panel for displaying tab content
 */
function TabPanel({ children, value, index, ...other }) {
  return (
    <div role="tabpanel" hidden={value !== index} id={`contract-tabpanel-${index}`} aria-labelledby={`contract-tab-${index}`} {...other}>
      {value === index && <Box sx={{ pt: '1.0rem' }}>{children}</Box>}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// MAIN COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

const ProviderContractView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { enqueueSnackbar } = useSnackbar();

  // ─────────────────────────────────────────────────────────────────────────
  // STATE
  // ─────────────────────────────────────────────────────────────────────────

  const [activeTab, setActiveTab] = useState(0);
  const [notesExpanded, setNotesExpanded] = useState(true);
  const [pricingSearch, setPricingSearch] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);
  const [pricingPage, setPricingPage] = useState(0);
  const [pricingRowsPerPage, setPricingRowsPerPage] = useState(10);

  // Dialog states
  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);
  const [terminateDialogOpen, setTerminateDialogOpen] = useState(false);
  const [suspendReason, setSuspendReason] = useState('');
  const [terminateReason, setTerminateReason] = useState('');

  // Pricing Dialog States
  const [addPricingDialogOpen, setAddPricingDialogOpen] = useState(false);
  const [editPricingDialogOpen, setEditPricingDialogOpen] = useState(false);
  const [deletePricingDialogOpen, setDeletePricingDialogOpen] = useState(false);
  const [excelImportDialogOpen, setExcelImportDialogOpen] = useState(false);
  const [selectedPricingFile, setSelectedPricingFile] = useState(null);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [uploadingPricingFile, setUploadingPricingFile] = useState(false);
  const [selectedPricingItem, setSelectedPricingItem] = useState(null);
  const [clearAllDialogOpen, setClearAllDialogOpen] = useState(false);
  const [pricingForm, setPricingForm] = useState({
    medicalServiceId: null,
    mainCategoryId: null,
    medicalCategoryId: null,
    basePrice: '',
    contractPrice: '',
    discountPercent: '',
    notes: ''
  });

  // ─────────────────────────────────────────────────────────────────────────
  // DATA FETCHING (Real API)
  // ─────────────────────────────────────────────────────────────────────────

  // Fetch contract details
  const {
    data: contract,
    isLoading,
    isError,
    error,
    refetch
  } = useQuery({
    queryKey: ['provider-contract', id],
    queryFn: () => getProviderContractById(id),
    enabled: !!id,
    retry: 1,
    staleTime: 30000
  });

  // Fetch pricing items
  const { data: pricingItemsData, isLoading: pricingLoading } = useQuery({
    queryKey: ['provider-contract-pricing', id, pricingPage, pricingRowsPerPage, pricingSearch, selectedCategoryId],
    queryFn: () =>
      getContractPricingItems(id, {
        page: pricingPage,
        size: pricingRowsPerPage,
        q: pricingSearch || undefined,
        categoryId: selectedCategoryId || undefined
      }),
    enabled: !!id,
    placeholderData: (prev) => prev
  });

  // NOTE: Medical services are now fetched dynamically by MedicalServiceSelector component

  // Fetch Medical Categories for Dropdown
  const { data: medicalCategories = [] } = useQuery({
    queryKey: ['medicalCategories'],
    queryFn: async () => {
      const data = await getAllMedicalCategories();
      return Array.isArray(data) ? data : (data?.data || []);
    },
    staleTime: 60 * 60 * 1000 // 1 hour
  });

  const mainCategoriesList = useMemo(() => {
    return medicalCategories.filter((c) => !c.parentId);
  }, [medicalCategories]);

  const subCategoriesList = useMemo(() => {
    if (!pricingForm.mainCategoryId) return [];
    return medicalCategories.filter((c) => c.parentId === pricingForm.mainCategoryId.id);
  }, [medicalCategories, pricingForm.mainCategoryId]);

  const [availableServices, setAvailableServices] = useState([]);
  const [servicesLoading, setServicesLoading] = useState(false);

  useEffect(() => {
    const fetchServices = async () => {
      // Use mainCategoryId if medicalCategoryId is removed
      const categoryId = pricingForm.medicalCategoryId?.id || pricingForm.mainCategoryId?.id;
      if (!categoryId) {
        setAvailableServices([]);
        return;
      }
      try {
        setServicesLoading(true);
        const services = await getMedicalServicesByCategory(categoryId);
        setAvailableServices(services || []);
      } catch (err) {
        console.error('Failed to fetch services:', err);
        setAvailableServices([]);
      } finally {
        setServicesLoading(false);
      }
    };
    fetchServices();
  }, [pricingForm.medicalCategoryId, pricingForm.mainCategoryId]);

  // ─────────────────────────────────────────────────────────────────────────
  // MUTATIONS
  // ─────────────────────────────────────────────────────────────────────────

  // Excel Upload Handler
  const handleExcelUpload = useCallback(
    async (file) => {
      try {
        setUploadingPricingFile(true);
        const result = await uploadContractPricingExcel(id, file);

        if (result.success) {
          const successCount = (result.summary?.created || 0) + (result.summary?.updated || 0);
          enqueueSnackbar(result.messageAr || result.message || `تم استيراد ${successCount} بند تسعير بنجاح`, {
            variant: 'success'
          });

          if (result.summary?.failed > 0) {
            enqueueSnackbar(`تحذير: فشل استيراد ${result.summary.failed} بند`, { variant: 'warning' });
          }

          // Refresh pricing items
          queryClient.invalidateQueries({ queryKey: ['provider-contract-pricing', id] });
          setExcelImportDialogOpen(false);
          setSelectedPricingFile(null);
        }
      } catch (error) {
        enqueueSnackbar(error?.message || 'فشل رفع الملف', { variant: 'error' });
      } finally {
        setUploadingPricingFile(false);
      }
    },
    [id, queryClient, enqueueSnackbar]
  );

  const handleImportPriceList = useCallback(() => {
    setExcelImportDialogOpen(true);
  }, []);

  const handleDownloadPricingTemplate = useCallback(async () => {
    try {
      setDownloadingTemplate(true);
      await downloadPricingTemplate(id);
      enqueueSnackbar('تم تحميل القالب بنجاح. الخطوة التالية: ارفع ملف الأسعار المعبأ.', { variant: 'success' });
    } catch (error) {
      enqueueSnackbar('فشل تحميل القالب', { variant: 'error' });
    } finally {
      setDownloadingTemplate(false);
    }
  }, [id, enqueueSnackbar]);

  const syncContractStatusCaches = useCallback(
    (updatedContract) => {
      if (!updatedContract) return;

      queryClient.setQueryData(['provider-contract', id], updatedContract);

      queryClient.setQueriesData({ queryKey: ['provider-contracts'] }, (oldData) => {
        if (!oldData) return oldData;

        const applyUpdate = (list) => {
          if (!Array.isArray(list)) return list;
          return list.map((item) => (String(item?.id) === String(updatedContract.id) ? { ...item, ...updatedContract } : item));
        };

        if (Array.isArray(oldData)) {
          return applyUpdate(oldData);
        }

        if (Array.isArray(oldData.content)) {
          return { ...oldData, content: applyUpdate(oldData.content) };
        }

        if (Array.isArray(oldData.items)) {
          return { ...oldData, items: applyUpdate(oldData.items) };
        }

        return oldData;
      });
    },
    [queryClient, id]
  );

  const activateMutation = useMutation({
    mutationFn: () => activateContract(id),
    onSuccess: (updatedContract) => {
      syncContractStatusCaches(updatedContract);
      enqueueSnackbar('تم تفعيل العقد بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
    },
    onError: (err) => {
      const errorMsg = err.response?.data?.message || err.message || 'فشل تفعيل العقد';
      if (errorMsg.includes('ACTIVE')) {
        enqueueSnackbar('العقد مُفعّل بالفعل', { variant: 'warning' });
        queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      } else {
        enqueueSnackbar(errorMsg, { variant: 'error' });
      }
    }
  });

  const suspendMutation = useMutation({
    mutationFn: (reason) => suspendContract(id, reason),
    onSuccess: (updatedContract) => {
      syncContractStatusCaches(updatedContract);
      enqueueSnackbar('تم إيقاف العقد بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      setSuspendDialogOpen(false);
      setSuspendReason('');
    },
    onError: (err) => {
      enqueueSnackbar(err.message || 'فشل إيقاف العقد', { variant: 'error' });
    }
  });

  const terminateMutation = useMutation({
    mutationFn: (reason) => terminateContract(id, reason),
    onSuccess: (updatedContract) => {
      syncContractStatusCaches(updatedContract);
      enqueueSnackbar('تم إلغاء العقد بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      queryClient.invalidateQueries({ queryKey: ['provider-contracts'] });
      setTerminateDialogOpen(false);
      setTerminateReason('');
    },
    onError: (err) => {
      enqueueSnackbar(err.message || 'فشل إلغاء العقد', { variant: 'error' });
    }
  });
  const deleteAllPricingMutation = useMutation({
    mutationFn: () => deleteAllPricingItems(id),
    onSuccess: (count) => {
      enqueueSnackbar(`تم حذف ${count} بند تسعير بنجاح. يمكنك الآن استيراد القائمة الجديدة.`, { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract-pricing', id] });
      queryClient.invalidateQueries({ queryKey: ['provider-contract', id] });
      setClearAllDialogOpen(false);
      setPricingPage(0);
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || err.message || 'فشل حذف بنود التسعير', { variant: 'error' });
    }
  });

  // Pricing CRUD Mutations
  const addPricingMutation = useMutation({
    mutationFn: (data) => addPricingItem(id, data),
    onSuccess: () => {
      enqueueSnackbar('تم إضافة الخدمة بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract-pricing', id] });
      setAddPricingDialogOpen(false);
      setPricingForm({
        medicalServiceId: null,
        mainCategoryId: null,
        medicalCategoryId: null,
        basePrice: '',
        contractPrice: '',
        discountPercent: '',
        notes: ''
      });
    },
    onError: (err) => {
      enqueueSnackbar(err.response?.data?.message || err.message || 'فشل إضافة الخدمة', { variant: 'error' });
    }
  });

  const updatePricingMutation = useMutation({
    mutationFn: (data) => updatePricingItem(selectedPricingItem.id, data),
    onSuccess: () => {
      enqueueSnackbar('تم تحديث الخدمة بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract-pricing', id] });
      setEditPricingDialogOpen(false);
      setSelectedPricingItem(null);
    },
    onError: (err) => {
      enqueueSnackbar(err.message || 'فشل تحديث الخدمة', { variant: 'error' });
    }
  });

  const deletePricingMutation = useMutation({
    mutationFn: () => deletePricingItem(selectedPricingItem.id),
    onSuccess: () => {
      enqueueSnackbar('تم حذف الخدمة بنجاح', { variant: 'success' });
      queryClient.invalidateQueries({ queryKey: ['provider-contract-pricing', id] });
      setDeletePricingDialogOpen(false);
      setSelectedPricingItem(null);
    },
    onError: (err) => {
      enqueueSnackbar(err.message || 'فشل حذف الخدمة', { variant: 'error' });
    }
  });

  // ─────────────────────────────────────────────────────────────────────────
  // COMPUTED VALUES
  // ─────────────────────────────────────────────────────────────────────────

  const statusConfig = useMemo(() => {
    return CONTRACT_STATUS_CONFIG[contract?.status] || { label: contract?.status, color: 'default' };
  }, [contract?.status]);

  const pricingModelConfig = useMemo(() => {
    return PRICING_MODEL_CONFIG[contract?.pricingModel] || { label: contract?.pricingModel };
  }, [contract?.pricingModel]);

  const pricingItems = useMemo(() => {
    return pricingItemsData?.content || contract?.pricingItems || [];
  }, [pricingItemsData, contract?.pricingItems]);

  const totalPricingItems = useMemo(() => {
    return pricingItemsData?.totalElements ?? pricingItems.length;
  }, [pricingItemsData, pricingItems]);

  const pricingTimeline = useMemo(() => {
    return [...pricingItems]
      .map((item) => {
        const hierarchy = getCategoryHierarchy(item, medicalCategories);
        return {
          id: item.id,
          serviceDisplay: getServiceDisplay(item),
          categoryMain: hierarchy.main,
          categorySub: hierarchy.sub,
          updatedAt: item.updatedAt || item.lastModifiedAt || item.modifiedAt || item.createdAt || null,
          updatedBy: item.updatedBy || item.lastModifiedBy || item.createdBy || '-',
          contractPrice: item.contractPrice
        };
      })
      .sort((a, b) => {
        if (!a.updatedAt && !b.updatedAt) return 0;
        if (!a.updatedAt) return 1;
        if (!b.updatedAt) return -1;
        return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
      });
  }, [pricingItems, medicalCategories]);

  // ─────────────────────────────────────────────────────────────────────────
  // HANDLERS
  // ─────────────────────────────────────────────────────────────────────────

  const handleBack = useCallback(() => {
    navigate('/provider-contracts');
  }, [navigate]);

  const handleEdit = useCallback(() => {
    navigate(`/provider-contracts/edit/${id}`);
  }, [navigate, id]);

  const handleTabChange = useCallback((event, newValue) => {
    setActiveTab(newValue);
  }, []);

  const handlePricingPageChange = useCallback((event, newPage) => {
    setPricingPage(newPage);
  }, []);

  const handlePricingRowsPerPageChange = useCallback((event) => {
    setPricingRowsPerPage(parseInt(event.target.value, 10));
    setPricingPage(0);
  }, []);

  const handleActivate = useCallback(() => {
    activateMutation.mutate();
  }, [activateMutation]);

  const handleSuspendConfirm = useCallback(() => {
    if (suspendReason.trim()) {
      suspendMutation.mutate(suspendReason);
    }
  }, [suspendMutation, suspendReason]);

  const handleTerminateConfirm = useCallback(() => {
    if (terminateReason.trim()) {
      terminateMutation.mutate(terminateReason);
    }
  }, [terminateMutation, terminateReason]);

  // Pricing Handlers
  const handleOpenAddPricing = useCallback(() => {
    setPricingForm({
      medicalServiceId: null,
      mainCategoryId: null,
      medicalCategoryId: null,
      basePrice: '',
      contractPrice: '',
      discountPercent: contract?.discountPercent ?? '',
      notes: ''
    });
    setAddPricingDialogOpen(true);
  }, [contract?.discountPercent]);

  const handleAddPricingSubmit = useCallback(async () => {
    const effectiveCategoryId = pricingForm.medicalCategoryId?.id || pricingForm.mainCategoryId?.id;
    if (!effectiveCategoryId || !pricingForm.medicalServiceId || !pricingForm.contractPrice)
      return;

    const selectedService = pricingForm.medicalServiceId;
    const parsedContractPrice = parseFloat(pricingForm.contractPrice);
    const parsedBasePrice = parseFloat(pricingForm.basePrice);

    addPricingMutation.mutate({
      medicalServiceId: selectedService?.id || null,
      medicalCategoryId: effectiveCategoryId || null,
      serviceCode: selectedService?.code || null,
      serviceName: selectedService?.nameAr || selectedService?.name || null,
      basePrice: Number.isFinite(parsedBasePrice) && parsedBasePrice > 0 ? parsedBasePrice : parsedContractPrice,
      contractPrice: parsedContractPrice,
      notes: pricingForm.notes
    });
  }, [addPricingMutation, pricingForm]);

  const handleOpenEditPricing = useCallback(
    (item) => {
      setSelectedPricingItem(item);
      const hierarchy = getCategoryHierarchy(item, medicalCategories);
      const catObj = getCategoryObject(item);
      const mainCat = catObj?.parentId ? medicalCategories.find((c) => c.id === catObj.parentId) : catObj;

      setPricingForm({
        medicalServiceId: item.medicalService || null,
        mainCategoryId: mainCat || null,
        medicalCategoryId: catObj || null,
        basePrice: item.basePrice ?? '',
        contractPrice: item.contractPrice ?? '',
        discountPercent: item.discountPercent ?? '',
        notes: item.notes || ''
      });
      setEditPricingDialogOpen(true);
    },
    [medicalCategories]
  );

  const handleEditPricingSubmit = useCallback(() => {
    if (!pricingForm.contractPrice) return;

    updatePricingMutation.mutate({
      medicalServiceId: pricingForm.medicalServiceId?.id || null,
      medicalCategoryId: pricingForm.medicalCategoryId?.id || pricingForm.mainCategoryId?.id || null,
      basePrice: parseFloat(pricingForm.contractPrice), // BasePrice becomes ContractPrice
      contractPrice: parseFloat(pricingForm.contractPrice),
      notes: pricingForm.notes
    });
  }, [updatePricingMutation, pricingForm]);

  // Bi-directional price/discount calculation
  const updatePriceFields = useCallback((updates) => {
    setPricingForm((prev) => {
      const newState = { ...prev, ...updates };

      // Rule: BP = ContractPrice / (1 - Discount/100)
      // Rule: CP = BasePrice * (1 - Discount/100)
      // Rule: Discount = (Base - Contract) / Base * 100

      if ('discountPercent' in updates) {
        const cp = parseFloat(newState.contractPrice) || 0;
        const dp = parseFloat(updates.discountPercent) || 0;
        if (cp > 0 && dp < 100) {
          newState.basePrice = Math.round((cp / (1 - dp / 100)) * 100) / 100;
        }
      } else if ('basePrice' in updates) {
        const bp = parseFloat(updates.basePrice) || 0;
        const cp = parseFloat(newState.contractPrice) || 0;
        if (bp > 0 && cp > 0) {
          newState.discountPercent = Math.round(((bp - cp) / bp) * 100);
        }
      } else if ('contractPrice' in updates) {
        const bp = parseFloat(newState.basePrice) || 0;
        const cp = parseFloat(updates.contractPrice) || 0;
        if (bp > 0) {
          newState.discountPercent = Math.round(((bp - cp) / bp) * 100);
        }
      }

      return newState;
    });
  }, []);

  const handleOpenDeletePricing = useCallback((item) => {
    setSelectedPricingItem(item);
    setDeletePricingDialogOpen(true);
  }, []);

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER - LOADING STATE
  // ─────────────────────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
        <CircularProgress />
      </Box>
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER - ERROR STATE
  // ─────────────────────────────────────────────────────────────────────────

  if (isError || !contract) {
    return (
      <MainCard>
        <Box display="flex" flexDirection="column" justifyContent="center" alignItems="center" minHeight={300} sx={{ py: '2.0rem' }}>
          <ContractIcon sx={{ fontSize: '4.0rem', color: 'error.main', mb: '1.0rem', opacity: 0.5 }} />
          <Typography variant="h6" color="error" gutterBottom>
            {isError ? 'خطأ في تحميل العقد' : 'العقد غير موجود'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: '1.0rem' }}>
            {error?.message || 'لم يتم العثور على العقد المطلوب'}
          </Typography>
          <Stack direction="row" spacing={2}>
            <Button variant="outlined" startIcon={<BackIcon />} onClick={handleBack}>
              العودة للقائمة
            </Button>
            <Button variant="contained" startIcon={<RefreshIcon />} onClick={() => refetch()}>
              إعادة المحاولة
            </Button>
          </Stack>
        </Box>
      </MainCard>
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER - CONTRACT VIEW
  // ─────────────────────────────────────────────────────────────────────────

  return (
    <>
      {/* Page Header */}
      <ModernPageHeader
        title={`عقد: ${contract.contractCode}`}
        subtitle={contract.providerName || contract.provider?.name || 'عقد مقدم خدمة'}
        icon={ContractIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/dashboard' },
          { label: 'عقود مقدمي الخدمة', path: '/provider-contracts' },
          { label: contract.contractCode, path: `/provider-contracts/${id}` }
        ]}
        actions={
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" color="inherit" startIcon={<BackIcon />} onClick={handleBack}>
              رجوع
            </Button>

            {/* Lifecycle Actions */}
            {(contract.status === CONTRACT_STATUS.DRAFT ||
              contract.status === CONTRACT_STATUS.SUSPENDED ||
              contract.status === CONTRACT_STATUS.TERMINATED) && (

              <Button
                variant="contained"
                color="success"
                startIcon={<ActivateIcon />}
                onClick={handleActivate}
                disabled={activateMutation.isLoading}
              >
                {contract.status === CONTRACT_STATUS.TERMINATED ? 'إعادة تفعيل' : 'تفعيل العقد'}
              </Button>

            )}

            {contract.status === CONTRACT_STATUS.ACTIVE && (

              <Button
                variant="outlined"
                color="warning"
                startIcon={<SuspendIcon />}
                onClick={() => setSuspendDialogOpen(true)}
                disabled={suspendMutation.isLoading}
              >
                إيقاف
              </Button>

            )}

            {(contract.status === CONTRACT_STATUS.ACTIVE || contract.status === CONTRACT_STATUS.SUSPENDED) && (

              <Button
                variant="outlined"
                color="error"
                startIcon={<TerminateIcon />}
                onClick={() => setTerminateDialogOpen(true)}
                disabled={terminateMutation.isLoading}
              >
                إلغاء
              </Button>

            )}


            <Button
              variant="outlined"
              color="primary"
              startIcon={<EditIcon />}
              onClick={handleEdit}
              disabled={contract.status === CONTRACT_STATUS.TERMINATED}
            >
              تعديل
            </Button>

          </Stack>
        }
      />

      {/* Contract Summary Card - Ultra Slim & Horizontal */}
      <MainCard sx={{ mb: '1.0rem', py: 0.5 }}>
        <Stack direction="row" spacing={4} alignItems="center" justifyContent="center" flexWrap="wrap">
          <InfoRow label="نموذج السعر" value={pricingModelConfig.label} icon={PriceIcon} />
          <InfoRow label="نسبة التخفيض" value={contract.discountPercent ? `${contract.discountPercent}%` : '-'} icon={PriceIcon} />
          <InfoRow label="عدد البنود" value={contract.pricingItemsCount || pricingItems.length} icon={InfoIcon} />
          <InfoRow label="بداية العقد" value={formatDate(contract.startDate)} icon={CalendarIcon} />
          <InfoRow label="نهاية العقد" value={formatDate(contract.endDate)} icon={CalendarIcon} />
          <Box flexGrow={1} />
          <Chip label={statusConfig.label} color={statusConfig.color} size="small" variant="combined" />
        </Stack>
      </MainCard>

      {/* Notes Section */}
      {contract.notes && (
        <MainCard
          title="ملاحظات"
          secondary={
            <Tooltip title={notesExpanded ? 'إخفاء الملاحظات' : 'إظهار الملاحظات'}>
              <IconButton size="small" onClick={() => setNotesExpanded((v) => !v)}>
                {notesExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          }
          sx={{ mb: '1.5rem' }}
        >
          <Collapse in={notesExpanded}>
            <Typography variant="body1" color="text.secondary">
              {contract.notes}
            </Typography>
          </Collapse>
        </MainCard>
      )}

      {/* Tabs Section */}
      <MainCard>
        <Tabs value={activeTab} onChange={handleTabChange} aria-label="contract tabs" sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tab label="بنود التسعير" id="contract-tab-0" />
          <Tab label="سجل بنود التسعير" id="contract-tab-1" />
        </Tabs>

        {/* Pricing Items Tab */}
        <TabPanel value={activeTab} index={0}>
          {/* Search and Excel Upload */}
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: '1.0rem' }} alignItems={{ xs: 'stretch', md: 'center' }}>
            <TextField
              placeholder="بحث في بنود التسعير..."
              value={pricingSearch}
              onChange={(e) => {
                setPricingSearch(e.target.value);
                setPricingPage(0);
              }}
              size="small"
              sx={{ flexGrow: 1, maxWidth: { xs: '100%', md: 420 } }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: pricingSearch ? (
                  <InputAdornment position="end">
                    <IconButton
                      size="small"
                      onClick={() => {
                        setPricingSearch('');
                        setPricingPage(0);
                      }}
                    >
                      <ClearIcon fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ) : null
              }}
            />

            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              <Autocomplete
                size="small"
                sx={{ minWidth: '12.5rem' }}
                options={medicalCategories || []}
                getOptionLabel={(option) => option.nameAr || option.name || option.code || ''}
                value={medicalCategories?.find((c) => c.id === selectedCategoryId) || null}
                onChange={(event, newValue) => {
                  setSelectedCategoryId(newValue?.id || null);
                  setPricingPage(0);
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    placeholder="فلترة حسب التصنيف"
                    size="small"
                  />
                )}
              />

              <Chip size="small" variant="outlined" color="primary" label={`${totalPricingItems} بند`} sx={{ width: 'fit-content' }} />
            </Box>

            <Box sx={{ flexGrow: 1 }} />

            <Stack direction="row" spacing={1}>
              {/* Add System Service Button */}
              <Button
                variant="contained"
                color="secondary"
                onClick={handleOpenAddPricing}
                startIcon={<AddIcon />}
                size="medium"
                disabled={contract.status === CONTRACT_STATUS.TERMINATED}
              >
                إضافة خدمة
              </Button>

              {/* Import Price List Button */}
              <Button
                variant="outlined"
                color="primary"
                onClick={handleImportPriceList}
                startIcon={<CloudUploadIcon />}
                size="medium"
                disabled={contract.status === CONTRACT_STATUS.TERMINATED}
              >
                استيراد الأسعار
              </Button>

              {/* Clear All Button */}
              {pricingItems.length > 0 && contract.status !== CONTRACT_STATUS.TERMINATED && (
                <Tooltip title="مسح جميع بنود التسعير الحالية">
                  <Button
                    variant="outlined"
                    color="error"
                    onClick={() => setClearAllDialogOpen(true)}
                    startIcon={<DeleteIcon />}
                    size="medium"
                  >
                    مسح الخدمات
                  </Button>
                </Tooltip>
              )}
            </Stack>


          </Stack>

          {/* Pricing Table */}
          <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: '35.0rem' }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow sx={{ backgroundColor: 'grey.50' }}>
                  <TableCell sx={{ width: '7.5rem' }}>كود الخدمة</TableCell>
                  <TableCell sx={{ minWidth: '15.625rem' }}>اسم الخدمة</TableCell>
                  <TableCell sx={{ minWidth: '11.25rem' }}>التصنيف الرئيسي</TableCell>
                  <TableCell sx={{ minWidth: '11.25rem' }}>البند (التصنيف الفرعي)</TableCell>
                  <TableCell align="right" sx={{ width: '7.5rem' }}>سعر العقد</TableCell>
                  <TableCell align="center" sx={{ width: '6.25rem' }}>الإجراءات</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {pricingLoading ? (
                  <TableRow>
                    <TableCell colSpan={9} align="center" sx={{ py: '2.0rem' }}>
                      <CircularProgress size={32} />
                    </TableCell>
                  </TableRow>
                ) : pricingItems.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={9} align="center" sx={{ py: '2.0rem' }}>
                      <Typography color="text.secondary">
                        {pricingSearch ? 'لم يتم العثور على بنود مطابقة' : 'لا توجد بنود تسعير'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  pricingItems.map((item, index) => (
                    <TableRow key={item.id || index} hover sx={{ '&:last-child td, &:last-child th': { border: 0 }, '&:nth-of-type(even)': { bgcolor: 'grey.25' } }}>
                      <TableCell>
                        {(() => {
                          const service = getServiceDisplay(item);
                          return (
                            <Box sx={{
                              bgcolor: 'primary.lighter',
                              color: 'primary.main',
                              px: 0.75,
                              py: 0.25,
                              borderRadius: 1,
                              fontSize: '0.75rem',
                              fontWeight: 700,
                              border: '1px solid',
                              borderColor: 'primary.light',
                              fontFamily: 'monospace',
                              width: 'fit-content'
                            }}>
                              {service.code}
                            </Box>
                          );
                        })()}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600} noWrap sx={{ maxWidth: '21.875rem' }}>
                          {getServiceDisplay(item).nameAr}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {(() => {
                          const hierarchy = getCategoryHierarchy(item, medicalCategories);
                          return (
                            <Stack spacing={0.25}>
                              {hierarchy.mainCode !== '-' && (
                                <Chip label={hierarchy.mainCode} size="small" variant="outlined" sx={{ width: 'fit-content', opacity: 0.7 }} />
                              )}
                              <Typography variant="body2" fontWeight={500}>
                                {hierarchy.main}
                              </Typography>
                            </Stack>
                          );
                        })()}
                      </TableCell>
                      <TableCell>
                        {(() => {
                          const hierarchy = getCategoryHierarchy(item, medicalCategories);
                          return (
                            <Stack spacing={0.25}>
                              {hierarchy.subCode !== '-' && (
                                <Chip label={hierarchy.subCode} size="small" variant="outlined" sx={{ width: 'fit-content', opacity: 0.7 }} />
                              )}
                              <Typography variant="body2" color="text.secondary">
                                {hierarchy.sub}
                              </Typography>
                            </Stack>
                          );
                        })()}
                      </TableCell>
                      <TableCell align="right">
                        <Typography fontWeight={700} color="primary.main">
                          {formatCurrency(item.contractPrice)}
                        </Typography>
                      </TableCell>

                      <TableCell align="center">
                        <Stack direction="row" spacing={1} justifyContent="center">

                          <Tooltip title="تعديل السعر">
                            <IconButton size="small" color="primary" onClick={() => handleOpenEditPricing(item)}>
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>


                          <Tooltip title="حذف">
                            <IconButton size="small" color="error" onClick={() => handleOpenDeletePricing(item)}>
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {/* Pagination */}
          {totalPricingItems > 0 && (
            <TablePagination
              component="div"
              count={totalPricingItems}
              page={pricingPage}
              onPageChange={handlePricingPageChange}
              rowsPerPage={pricingRowsPerPage}
              onRowsPerPageChange={handlePricingRowsPerPageChange}
              rowsPerPageOptions={[5, 10, 25, 50]}
              labelRowsPerPage="عدد الصفوف:"
              labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
            />
          )}
        </TabPanel>

        {/* Change Log Tab (Future) */}
        <TabPanel value={activeTab} index={1}>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow sx={{ backgroundColor: 'grey.50' }}>
                  <TableCell>الخدمة</TableCell>
                  <TableCell>التصنيف</TableCell>
                  <TableCell align="right">سعر العقد</TableCell>
                  <TableCell>آخر تعديل</TableCell>
                  <TableCell>بواسطة</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {pricingTimeline.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center" sx={{ py: '2.0rem' }}>
                      <Typography color="text.secondary">لا يوجد سجل متاح حالياً</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  pricingTimeline.map((entry) => (
                    <TableRow key={entry.id} hover>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" fontWeight={600}>
                            {entry.serviceDisplay.code} - {entry.serviceDisplay.nameAr}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {entry.serviceDisplay.nameEn}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" fontWeight={500}>
                            {entry.categoryMain}
                          </Typography>
                          {entry.categorySub !== '-' && (
                            <Typography variant="caption" color="text.secondary">
                              {entry.categorySub}
                            </Typography>
                          )}
                        </Stack>
                      </TableCell>
                      <TableCell align="right">{formatCurrency(entry.contractPrice)}</TableCell>
                      <TableCell>{formatDate(entry.updatedAt)}</TableCell>
                      <TableCell>{entry.updatedBy}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </TabPanel>
      </MainCard>

      {/* Suspend Dialog */}
      <Dialog open={suspendDialogOpen} onClose={() => setSuspendDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>إيقاف العقد</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: '1.0rem' }}>هل أنت متأكد من إيقاف العقد؟ يرجى إدخال سبب الإيقاف.</DialogContentText>
          <TextField
            autoFocus
            label="سبب الإيقاف"
            fullWidth
            multiline
            rows={3}
            value={suspendReason}
            onChange={(e) => setSuspendReason(e.target.value)}
            required
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSuspendDialogOpen(false)}>إلغاء</Button>
          <Button
            onClick={handleSuspendConfirm}
            color="warning"
            variant="contained"
            disabled={!suspendReason.trim() || suspendMutation.isLoading}
          >
            {suspendMutation.isLoading ? <CircularProgress size={20} /> : 'إيقاف العقد'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Terminate Dialog */}
      <Dialog open={terminateDialogOpen} onClose={() => setTerminateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle color="error">إلغاء العقد</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: '1.0rem' }}>
            <strong>تحذير:</strong> إلغاء العقد إجراء نهائي ولا يمكن التراجع عنه. يرجى إدخال سبب الإلغاء.
          </DialogContentText>
          <TextField
            autoFocus
            label="سبب الإلغاء"
            fullWidth
            multiline
            rows={3}
            value={terminateReason}
            onChange={(e) => setTerminateReason(e.target.value)}
            required
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTerminateDialogOpen(false)}>تراجع</Button>
          <Button
            onClick={handleTerminateConfirm}
            color="error"
            variant="contained"
            disabled={!terminateReason.trim() || terminateMutation.isLoading}
          >
            {terminateMutation.isLoading ? <CircularProgress size={20} /> : 'إلغاء العقد نهائياً'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Add Pricing Item Dialog */}
      <Dialog open={addPricingDialogOpen} onClose={() => setAddPricingDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>إضافة خدمة طبية للتسعير</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: '1.5rem' }}>
            اختر التصنيف أولاً ثم الخدمة من القاموس وأدخل السعر المتفق عليه.
          </DialogContentText>
          <Stack spacing={3}>
              <Autocomplete
                fullWidth
                sx={{ width: '100%' }}
                options={mainCategoriesList}
                getOptionLabel={(option) => `${option.code || ''} - ${option.nameAr || option.name || ''}`}
                value={pricingForm.mainCategoryId}
                onChange={(e, newValue) => {
                  setPricingForm({
                    ...pricingForm,
                    mainCategoryId: newValue,
                    medicalCategoryId: null,
                    medicalServiceId: null
                  });
                }}
                renderInput={(params) => <TextField {...params} label="التصنيف الرئيسي *" required fullWidth />}
              />

            {/* Subcategory - shown when main category is chosen and subcategories exist */}
            {(pricingForm.mainCategoryId && subCategoriesList.length > 0) && (
              <Autocomplete
                fullWidth
                options={subCategoriesList}
                getOptionLabel={(option) => `${option.code || ''} - ${option.nameAr || option.name || ''}`}
                value={pricingForm.medicalCategoryId}
                onChange={(e, newValue) => {
                  setPricingForm({
                    ...pricingForm,
                    medicalCategoryId: newValue,
                    medicalServiceId: null
                  });
                }}
                renderInput={(params) => <TextField {...params} label="التصنيف الفرعي (اختياري)" fullWidth placeholder="اختر للتصفية" />}
              />
            )}

            {(pricingForm.mainCategoryId) && (
              <Autocomplete
                fullWidth
                options={availableServices}
                getOptionLabel={(option) =>
                  `[${option.code || ''}] ${option.nameAr || option.name || ''}`
                }
                ListboxProps={{ style: { maxHeight: '20.0rem', minHeight: '7.5rem' } }}
                loading={servicesLoading}
                value={pricingForm.medicalServiceId}
                renderOption={(props, option) => (
                  <li {...props} key={option.id}>
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      <span style={{ fontWeight: 600 }}>{option.nameAr || option.name || ''}</span>
                      <span style={{ fontSize: '0.75rem', color: '#888' }}>
                        {option.code}{option.nameEn ? ` • ${option.nameEn}` : ''}
                      </span>
                    </div>
                  </li>
                )}
                onChange={(e, newValue) => {
                  setPricingForm({
                    ...pricingForm,
                    medicalServiceId: newValue,
                    basePrice: newValue?.basePrice ?? '',
                    contractPrice:
                      newValue?.basePrice && pricingForm.discountPercent
                        ? (newValue.basePrice * (1 - parseFloat(pricingForm.discountPercent) / 100)).toFixed(2)
                        : pricingForm.contractPrice
                  });
                }}
                renderInput={(params) => <TextField {...params} label="الخدمة الطبية *" required fullWidth />}
              />
            )}


            <TextField
              label="سعر الخدمة (المتفق عليه) *"
              type="number"
              fullWidth
              value={pricingForm.contractPrice}
              onChange={(e) => updatePriceFields({ contractPrice: e.target.value })}
              required
              helperText="أدخل السعر الصافي المتفق عليه لهذا البند"
            />

            <TextField
              label="ملاحظات"
              fullWidth
              multiline
              rows={2}
              value={pricingForm.notes}
              onChange={(e) => setPricingForm({ ...pricingForm, notes: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddPricingDialogOpen(false)}>إلغاء</Button>
          <Button
            onClick={handleAddPricingSubmit}
            variant="contained"
            disabled={
              !pricingForm.mainCategoryId ||
              !pricingForm.medicalServiceId ||
              !pricingForm.contractPrice ||
              addPricingMutation.isLoading
            }
          >
            {addPricingMutation.isLoading ? <CircularProgress size={20} /> : 'إضافة'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Pricing Item Dialog */}
      <Dialog open={editPricingDialogOpen} onClose={() => setEditPricingDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>تعديل سعر الخدمة</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: '1.5rem' }}>
            {selectedPricingItem
              ? `تعديل السعر للخدمة: ${getServiceCode(selectedPricingItem)} - ${getServiceNameAr(selectedPricingItem)} - ${getServiceNameEn(selectedPricingItem)}`
              : 'تعديل السعر'}
          </DialogContentText>
          <Stack spacing={3}>
            <Grid container spacing={2}>
              <Grid size={12}>
                <Autocomplete
                  fullWidth
                  sx={{ width: '100%' }}
                  options={mainCategoriesList}
                  getOptionLabel={(option) => `${option.code || ''} - ${option.nameAr || option.name || ''}`}
                  value={pricingForm.mainCategoryId}
                  onChange={(e, newValue) => {
                    setPricingForm({
                      ...pricingForm,
                      mainCategoryId: newValue,
                      medicalCategoryId: null
                    });
                  }}
                  renderInput={(params) => <TextField {...params} label="التصنيف الرئيسي" fullWidth placeholder="اختر للتغيير" />}
                />
              </Grid>
            </Grid>

            {/* Sub category (if applicable) */}
            {(pricingForm.mainCategoryId && subCategoriesList.length > 0) && (
              <Autocomplete
                fullWidth
                options={subCategoriesList}
                getOptionLabel={(option) => `${option.code || ''} - ${option.nameAr || option.name || ''}`}
                value={pricingForm.medicalCategoryId}
                onChange={(e, newValue) => {
                  setPricingForm({
                    ...pricingForm,
                    medicalCategoryId: newValue
                  });
                }}
                renderInput={(params) => <TextField {...params} label="التصنيف الفرعي (اختياري)" fullWidth placeholder="اختر للتصفية" />}
              />
            )}


            <TextField
              label="سعر الخدمة الجديد *"
              type="number"
              fullWidth
              value={pricingForm.contractPrice}
              onChange={(e) => updatePriceFields({ contractPrice: e.target.value })}
              required
              helperText="أدخل السعر الجديد المتفق عليه"
            />

            <TextField
              label="ملاحظات"
              fullWidth
              multiline
              rows={2}
              value={pricingForm.notes}
              onChange={(e) => setPricingForm({ ...pricingForm, notes: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditPricingDialogOpen(false)}>إلغاء</Button>
          <Button
            onClick={handleEditPricingSubmit}
            variant="contained"
            disabled={!pricingForm.contractPrice || updatePricingMutation.isLoading}
          >
            {updatePricingMutation.isLoading ? <CircularProgress size={20} /> : 'حفظ التغييرات'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Pricing Dialog */}
      <Dialog open={deletePricingDialogOpen} onClose={() => setDeletePricingDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle color="error">حذف الخدمة</DialogTitle>
        <DialogContent>
          <DialogContentText>
            هل أنت متأكد من حذف هذه الخدمة من العقد؟
            <br />
            <strong>{selectedPricingItem?.serviceName || selectedPricingItem?.medicalService?.name}</strong>
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeletePricingDialogOpen(false)}>إلغاء</Button>
          <Button
            onClick={() => deletePricingMutation.mutate()}
            color="error"
            variant="contained"
            disabled={deletePricingMutation.isLoading}
          >
            {deletePricingMutation.isLoading ? <CircularProgress size={20} /> : 'حذف'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Clear All Pricing Items Dialog */}
      <Dialog open={clearAllDialogOpen} onClose={() => setClearAllDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle color="error" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <DeleteIcon /> مسح الخدمات الحالية
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            سيؤدي هذا الإجراء إلى <strong>مسح جميع</strong> بنود التسعير الحالية (<strong>{totalPricingItems} بند</strong>) من هذا العقد نهائياً.
            <br /><br />
            ⚠️ هذا الإجراء لا يمكن التراجع عنه. هل أنت متأكد؟
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setClearAllDialogOpen(false)} disabled={deleteAllPricingMutation.isLoading}>إلغاء</Button>
          <Button
            onClick={() => deleteAllPricingMutation.mutate()}
            color="error"
            variant="contained"
            disabled={deleteAllPricingMutation.isLoading}
          >
            {deleteAllPricingMutation.isLoading ? <CircularProgress size={20} /> : 'نعم، مسح الكل'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Excel Import Dialog (Two Steps) */}
      <Dialog open={excelImportDialogOpen} onClose={() => !uploadingPricingFile && setExcelImportDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>استيراد قائمة الأسعار (Excel)</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={3}>
            <Alert severity="info" icon={<DownloadIcon />}>
              الخطوة 1: حمّل القالب الرسمي ثم عبّئ أسماء الخدمات والأسعار.
              <Box mt={1}>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={handleDownloadPricingTemplate}
                  disabled={downloadingTemplate || uploadingPricingFile}
                  startIcon={downloadingTemplate ? <CircularProgress size={16} /> : <DownloadIcon />}
                >
                  {downloadingTemplate ? 'جار التحميل...' : 'تحميل القالب'}
                </Button>
              </Box>
            </Alert>

            <Alert severity="info" icon={<CloudUploadIcon />}>
              الخطوة 2: ارفع الملف المعبأ. إذا كان السعر فارغًا سيتم حفظه 0 تلقائيًا.
            </Alert>

            <Box
              component="label"
              sx={{
                border: '2px dashed',
                borderColor: selectedPricingFile ? 'success.main' : 'divider',
                borderRadius: '0.25rem',
                p: '2.0rem',
                textAlign: 'center',
                backgroundColor: selectedPricingFile ? 'success.lighter' : 'background.paper',
                cursor: uploadingPricingFile ? 'default' : 'pointer'
              }}
            >
              <input
                type="file"
                hidden
                accept=".xlsx,.xls"
                disabled={uploadingPricingFile}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  setSelectedPricingFile(file);
                }}
              />

              <Stack spacing={1} alignItems="center">
                {selectedPricingFile ? (
                  <>
                    <FileIcon color="success" sx={{ fontSize: '2.625rem' }} />
                    <Typography variant="body1" fontWeight={600}>
                      {selectedPricingFile.name}
                    </Typography>
                  </>
                ) : (
                  <>
                    <CloudUploadIcon color="action" sx={{ fontSize: '2.625rem' }} />
                    <Typography variant="body2" color="text.secondary">
                      اضغط لاختيار ملف Excel المعبأ
                    </Typography>
                  </>
                )}
              </Stack>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setExcelImportDialogOpen(false)} disabled={uploadingPricingFile}>
            إلغاء
          </Button>
          <Button
            variant="contained"
            startIcon={uploadingPricingFile ? <CircularProgress size={18} color="inherit" /> : <CloudUploadIcon />}
            onClick={() => {
              if (selectedPricingFile) {
                handleExcelUpload(selectedPricingFile);
              } else {
                enqueueSnackbar('يرجى اختيار ملف Excel أولاً', { variant: 'warning' });
              }
            }}
            disabled={!selectedPricingFile || uploadingPricingFile}
          >
            {uploadingPricingFile ? 'جار الرفع...' : 'رفع واستيراد'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ProviderContractView;


