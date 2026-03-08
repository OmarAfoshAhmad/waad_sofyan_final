/**
 * Medical Catalog Page — Unified hierarchical view of medical categories and services.
 *
 * Features:
 *  - Category accordion tree (expand / collapse)
 *  - Debounced search (code, name_ar, name_en, aliases)
 *  - Toggle: Show Master Only
 *  - Expand All / Collapse All
 *  - isMaster badge + Active/Inactive chip per service
 *
 * RTL Arabic-first layout.
 * Access: SUPER_ADMIN, DATA_ENTRY.
 */
import { useState, useCallback, useRef, useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Badge,
  Box,
  Button,
  ButtonGroup,
  Checkbox,
  Chip,
  CircularProgress,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';

import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SearchIcon from '@mui/icons-material/Search';
import StarIcon from '@mui/icons-material/Star';
import FilterListIcon from '@mui/icons-material/FilterList';
import UnfoldMoreIcon from '@mui/icons-material/UnfoldMore';
import UnfoldLessIcon from '@mui/icons-material/UnfoldLess';
import ClearIcon from '@mui/icons-material/Clear';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import CategoryIcon from '@mui/icons-material/Category';

import { getCatalogTree, searchCatalog, getSpecialties } from 'services/medical/medicalCatalog.service';
import { getAllMedicalServices, updateServiceCategory, bulkUpdateServiceCategory } from 'services/api/medical-services.service';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';
import ExcelImportButton from 'components/ExcelImport/ExcelImportButton';
import { openSnackbar } from 'api/snackbar';

// ─── Debounce hook ─────────────────────────────────────────────────────────────
function useDebounce(fn, delay = 350) {
  const timer = useRef(null);
  return useCallback(
    (...args) => {
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => fn(...args), delay);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [fn, delay]
  );
}

// ─── Status chip ──────────────────────────────────────────────────────────────
function StatusChip({ active }) {
  return (
    <Chip
      label={active ? 'نشط' : 'غير نشط'}
      size="small"
      color={active ? 'success' : 'default'}
      variant="outlined"
      sx={{ fontSize: '0.7rem', height: 20 }}
    />
  );
}

// ─── Master badge ─────────────────────────────────────────────────────────────
function MasterBadge() {
  return (
    <Tooltip title="خدمة رئيسية (Master)">
      <StarIcon sx={{ fontSize: 14, color: 'warning.main', ml: 0.5, verticalAlign: 'middle' }} />
    </Tooltip>
  );
}

// ─── Service row ──────────────────────────────────────────────────────────────
function ServiceRow({ service, draggable = false, onDragStart = null, onClick = null, selected = false }) {
  const label = service.nameAr || service.nameEn || service.name || service.serviceName || service.code;
  return (
    <ListItem
      disableGutters
      divider
      onClick={onClick}
      draggable={draggable}
      onDragStart={
        draggable
          ? (e) => {
            e.dataTransfer.setData('text/plain', String(service.id));
            e.dataTransfer.effectAllowed = 'move';
            onDragStart?.(service.id);
          }
          : undefined
      }
      sx={{ py: 0.5, px: 1.5, '&:last-child': { borderBottom: 0 }, cursor: onClick ? 'pointer' : 'default', bgcolor: selected ? 'primary.lighter' : 'transparent' }}
    >
      <ListItemText
        primary={
          <Stack direction="row" spacing={1} alignItems="center">
            {draggable && <DragIndicatorIcon sx={{ fontSize: 16, color: 'text.disabled' }} />}
            {typeof onClick === 'function' && (
              <Checkbox
                size="small"
                checked={selected}
                onClick={(e) => e.stopPropagation()}
                onChange={() => onClick()}
              />
            )}
            <Typography variant="body2" sx={{ fontWeight: 500, minWidth: 80, color: 'text.secondary', fontSize: '0.75rem' }}>
              {service.code}
            </Typography>
            <Typography variant="body2" sx={{ flex: 1 }}>
              {label}
              {service.isMaster && <MasterBadge />}
            </Typography>
            <StatusChip active={service.active} />
          </Stack>
        }
        secondary={
          service.nameEn || service.name || service.specialtyNameAr ? (
            <Typography variant="caption" color="text.disabled" sx={{ fontFamily: 'inherit' }}>
              {service.nameEn ?? service.name ?? ''}
              {service.specialtyNameAr ? `  •  ${service.specialtyNameAr}` : ''}
            </Typography>
          ) : null
        }
      />
    </ListItem>
  );
}

// ─── Category accordion ───────────────────────────────────────────────────────
function CategoryAccordion({ category, expanded, onToggle, onDropService, onDragServiceStart, selectedServiceCount, onAssignSelected, onCreateSubcategory }) {
  const label = category.nameAr || category.nameEn || category.code;
  const count = category.services?.length ?? 0;
  const [isOver, setIsOver] = useState(false);

  return (
    <Accordion
      expanded={expanded}
      onChange={onToggle}
      disableGutters
      elevation={0}
      sx={{
        border: '1px solid',
        borderColor: isOver ? 'primary.main' : 'divider',
        borderRadius: '8px !important',
        mb: 1,
        boxShadow: isOver ? 2 : 0,
        '&:before': { display: 'none' }
      }}
      onDragOver={(e) => {
        e.preventDefault();
        setIsOver(true);
      }}
      onDragLeave={() => setIsOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setIsOver(false);
        const raw = e.dataTransfer.getData('text/plain');
        const serviceId = Number(raw);
        if (serviceId) onDropService?.(serviceId, category.categoryId);
      }}
    >
      <AccordionSummary
        expandIcon={<ExpandMoreIcon />}
        sx={{
          bgcolor: 'grey.50',
          borderRadius: expanded ? '8px 8px 0 0' : '8px',
          minHeight: 48
        }}
      >
        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ flex: 1, pr: 1 }}>
          <Typography sx={{ fontSize: '0.8rem', color: 'text.secondary', fontFamily: 'monospace', minWidth: 80 }}>
            {category.code}
          </Typography>
          <Typography variant="subtitle2" sx={{ flex: 1 }}>
            {label}
          </Typography>
          <Button
            component="div"
            size="small"
            variant="text"
            onClick={(e) => {
              e.stopPropagation();
              onCreateSubcategory?.(category.categoryId);
            }}
          >
            إضافة فرعي
          </Button>
          {selectedServiceCount > 0 && (
            <Button
              component="div"
              size="small"
              variant="text"
              onClick={(e) => {
                e.stopPropagation();
                onAssignSelected?.(category.categoryId);
              }}
            >
              ربط المحدد هنا ({selectedServiceCount})
            </Button>
          )}
          <Chip
            size="small"
            label={count}
            color="primary"
            variant="filled"
            sx={{ height: 20, fontSize: '0.7rem', borderRadius: 2, fontWeight: 700 }}
          />
        </Stack>
      </AccordionSummary>
      <AccordionDetails sx={{ p: 0 }}>
        {count > 0 ? (
          <List dense disablePadding>
            {category.services.map((svc) => (
              <ServiceRow key={svc.id} service={svc} draggable onDragStart={onDragServiceStart} />
            ))}
          </List>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
            لا توجد خدمات مرتبطة
          </Typography>
        )}
      </AccordionDetails>
    </Accordion>
  );
}

// ─── Search result row ────────────────────────────────────────────────────────
function SearchResultRow({ item }) {
  const label = item.nameAr || item.nameEn || item.name || item.serviceName || item.code;
  return (
    <ListItem
      disableGutters
      divider
      sx={{ py: 0.75, px: 1.5, '&:last-child': { borderBottom: 0 } }}
    >
      <ListItemText
        primary={
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="body2" sx={{ fontWeight: 500, minWidth: 80, color: 'text.secondary', fontSize: '0.75rem' }}>
              {item.code}
            </Typography>
            <Typography variant="body2" sx={{ flex: 1 }}>
              {label}
              {item.isMaster && <MasterBadge />}
            </Typography>
            <StatusChip active={item.active} />
          </Stack>
        }
        secondary={
          item.categoryNameAr || item.categoryName || item.specialtyNameAr ? (
            <Typography variant="caption" color="text.disabled">
              {item.categoryCode ? `${item.categoryCode} — ${item.categoryNameAr || item.categoryName || ''}` : ''}
              {item.specialtyNameAr ? `  •  ${item.specialtyNameAr}` : ''}
            </Typography>
          ) : null
        }
      />
    </ListItem>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────
export default function MedicalCatalogPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  // Search state
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  // Filters
  const [masterOnly, setMasterOnly] = useState(false);
  const [selectedSpecialty, setSelectedSpecialty] = useState(''); // specialty code or '' = all

  // Accordion expansion set
  const [expandedIds, setExpandedIds] = useState(new Set());
  const [draggedServiceId, setDraggedServiceId] = useState(null);
  const [selectedServiceIds, setSelectedServiceIds] = useState([]);
  const [selectedTargetCategoryId, setSelectedTargetCategoryId] = useState('');
  const [bulkAssigning, setBulkAssigning] = useState(false);

  // ── Data queries ─────────────────────────────────────────────────────────
  const { data: tree = [], isLoading: treeLoading, error: treeError } = useQuery({
    queryKey: ['medical-catalog-tree'],
    queryFn: getCatalogTree,
    staleTime: 10 * 60 * 1000, // 10 min — data changes infrequently
    gcTime: 30 * 60 * 1000     // 30 min — keep in memory
  });

  const { data: specialties = [] } = useQuery({
    queryKey: ['medical-specialties'],
    queryFn: getSpecialties,
    staleTime: 30 * 60 * 1000, // 30 min — specialties rarely change
    gcTime: 60 * 60 * 1000
  });

  const { data: searchResults = [], isLoading: searchLoading } = useQuery({
    queryKey: ['medical-catalog-search', searchQuery],
    queryFn: () => searchCatalog(searchQuery),
    enabled: searchQuery.trim().length > 0,
    staleTime: 60 * 1000 // 1 min
  });

  const { data: allServices = [] } = useQuery({
    queryKey: ['medical-services-all'],
    queryFn: getAllMedicalServices,
    staleTime: 5 * 60 * 1000,
    gcTime: 15 * 60 * 1000
  });

  const { data: allCategories = [] } = useQuery({
    queryKey: ['medical-categories-all'],
    queryFn: getAllMedicalCategories,
    staleTime: 10 * 60 * 1000,
    gcTime: 30 * 60 * 1000
  });

  const assignCategoryMutation = useMutation({
    mutationFn: ({ serviceId, categoryId }) => updateServiceCategory(serviceId, categoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['medical-catalog-tree'] });
      queryClient.invalidateQueries({ queryKey: ['medical-services-all'] });
      queryClient.invalidateQueries({ queryKey: ['medical-catalog-search'] });
      openSnackbar({
        open: true,
        message: 'تم ربط الخدمة بالتصنيف بنجاح',
        variant: 'alert',
        alert: { color: 'success' }
      });
    },
    onError: (err) => {
      openSnackbar({
        open: true,
        message: err?.response?.data?.message || 'فشل ربط الخدمة بالتصنيف',
        variant: 'alert',
        alert: { color: 'error' }
      });
    }
  });

  const handleCatalogImportComplete = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['medical-catalog-tree'] });
    queryClient.invalidateQueries({ queryKey: ['medical-services-all'] });
    queryClient.invalidateQueries({ queryKey: ['medical-categories-all'] });
    queryClient.invalidateQueries({ queryKey: ['medical-catalog-search'] });
  }, [queryClient]);

  // ── Debounced search ──────────────────────────────────────────────────────
  const fireSearch = useCallback((val) => setSearchQuery(val), []);
  const debouncedSearch = useDebounce(fireSearch, 350);

  const handleSearchChange = (e) => {
    const val = e.target.value;
    setSearchInput(val);
    debouncedSearch(val);
  };

  const clearSearch = () => {
    setSearchInput('');
    setSearchQuery('');
  };

  // ── Tree filtering ────────────────────────────────────────────────────────
  const filteredTree = useMemo(
    () => tree.map((cat) => ({ ...cat })).filter(Boolean),
    [tree]
  );

  const normalizedTree = useMemo(() => {
    const servicesByCategoryId = new Map(
      filteredTree.map((cat) => [
        cat.categoryId,
        (cat.services ?? []).filter((s) => {
          if (masterOnly && !s.isMaster) return false;
          if (selectedSpecialty && s.specialtyCode !== selectedSpecialty) return false;
          return true;
        })
      ])
    );

    const hasCatalogCategories = Array.isArray(allCategories) && allCategories.length > 0;
    const categoriesSource = hasCatalogCategories
      ? allCategories.map((c) => ({
        categoryId: c.id,
        code: c.code,
        nameAr: c.nameAr || c.name,
        nameEn: c.nameEn || c.name
      }))
      : filteredTree;

    return categoriesSource.map((cat) => ({
      ...cat,
      services: servicesByCategoryId.get(cat.categoryId) || []
    }));
  }, [filteredTree, allCategories, masterOnly, selectedSpecialty]);

  const uncategorizedServices = useMemo(() => {
    if (!Array.isArray(allServices)) return [];
    return allServices
      .filter((s) => {
        const categoryId = s.categoryId ?? s.medicalCategoryId ?? null;
        if (categoryId !== null && categoryId !== undefined && categoryId !== '') return false;
        if (masterOnly && !s.isMaster) return false;
        if (selectedSpecialty && s.specialtyCode !== selectedSpecialty) return false;
        return true;
      })
      .slice(0, 200);
  }, [allServices, masterOnly, selectedSpecialty]);

  // ── Expand / Collapse All ─────────────────────────────────────────────────
  const allIds = normalizedTree.map((c) => c.categoryId);

  const expandAll = () => setExpandedIds(new Set(allIds));
  const collapseAll = () => setExpandedIds(new Set());

  const toggleCategory = (id) =>
    setExpandedIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  // ── Derived stats ─────────────────────────────────────────────────────────
  const totalCategories = normalizedTree.length;
  const totalServices = normalizedTree.reduce((acc, c) => acc + (c.services?.length ?? 0), 0);

  const isSearchMode = searchQuery.trim().length > 0;

  const handleServiceDrop = useCallback(
    (serviceId, categoryId) => {
      if (!serviceId || !categoryId) return;
      assignCategoryMutation.mutate({ serviceId, categoryId });
      setDraggedServiceId(null);
      setSelectedServiceIds([]);
      setSelectedTargetCategoryId('');
    },
    [assignCategoryMutation]
  );

  const toggleUncategorizedSelection = useCallback((serviceId) => {
    setSelectedServiceIds((prev) => (prev.includes(serviceId) ? prev.filter((id) => id !== serviceId) : [...prev, serviceId]));
  }, []);

  const selectAllUncategorized = useCallback(() => {
    setSelectedServiceIds(uncategorizedServices.map((svc) => svc.id));
  }, [uncategorizedServices]);

  const clearUncategorizedSelection = useCallback(() => {
    setSelectedServiceIds([]);
  }, []);

  const assignServicesToCategory = useCallback(
    async (serviceIds, categoryId) => {
      if (!categoryId || !Array.isArray(serviceIds) || serviceIds.length === 0 || bulkAssigning) return;

      try {
        setBulkAssigning(true);
        const normalizedServiceIds = serviceIds.map((id) => Number(id)).filter((id) => Number.isFinite(id));

        if (normalizedServiceIds.length === 0) {
          throw new Error('لا توجد خدمات صالحة للربط');
        }

        if (serviceIds.length === 1) {
          await updateServiceCategory(normalizedServiceIds[0], Number(categoryId));
        } else {
          await bulkUpdateServiceCategory(normalizedServiceIds, Number(categoryId));
        }

        queryClient.invalidateQueries({ queryKey: ['medical-catalog-tree'] });
        queryClient.invalidateQueries({ queryKey: ['medical-services-all'] });
        queryClient.invalidateQueries({ queryKey: ['medical-catalog-search'] });

        openSnackbar({
          open: true,
          message: `تم ربط ${serviceIds.length} خدمة بالتصنيف بنجاح`,
          variant: 'alert',
          alert: { color: 'success' }
        });

        setSelectedServiceIds([]);
        setSelectedTargetCategoryId('');
      } catch (err) {
        openSnackbar({
          open: true,
          message: err?.response?.data?.message || 'فشل ربط الخدمات المحددة',
          variant: 'alert',
          alert: { color: 'error' }
        });
      } finally {
        setBulkAssigning(false);
      }
    },
    [bulkAssigning, queryClient]
  );

  const handleBulkAssignSelected = useCallback(async () => {
    if (!selectedTargetCategoryId || selectedServiceIds.length === 0 || bulkAssigning) return;
    await assignServicesToCategory(selectedServiceIds, selectedTargetCategoryId);
  }, [assignServicesToCategory, bulkAssigning, selectedServiceIds, selectedTargetCategoryId]);

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <Box dir="rtl" sx={{ p: { xs: 1, md: 2 }, maxWidth: 960, mx: 'auto' }}>
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={2} flexWrap="wrap" gap={1}>
        <Box>
          <Typography variant="h5" fontWeight={700}>
            الكتالوج الطبي الموحد
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Unified Medical Catalog
          </Typography>
        </Box>

        {!isSearchMode && !treeLoading && (
          <Stack direction="row" spacing={1} alignItems="center">
            <ExcelImportButton
              module="medical-services"
              buttonLabel="استيراد خدمات Excel"
              buttonVariant="outlined"
              buttonColor="primary"
              onImportComplete={handleCatalogImportComplete}
            />
            <ExcelImportButton
              module="medical-categories"
              buttonLabel="استيراد تصنيفات Excel"
              buttonVariant="outlined"
              buttonColor="secondary"
              onImportComplete={handleCatalogImportComplete}
            />
            <Button size="small" variant="outlined" startIcon={<CategoryIcon />} onClick={() => navigate('/medical-categories')}>
              إدارة التصنيفات
            </Button>
            <Chip
              size="small"
              label={`${totalCategories} تصنيف`}
              color="primary"
              variant="outlined"
            />
            <Chip
              size="small"
              label={`${totalServices} خدمة`}
              color="secondary"
              variant="outlined"
            />
          </Stack>
        )}
      </Stack>

      {/* Search + Toolbar */}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} mb={2.5} alignItems="flex-start">
        {/* Search box */}
        <TextField
          size="small"
          placeholder="ابحث برمز الخدمة، الاسم، أو الاسم البديل..."
          value={searchInput}
          onChange={handleSearchChange}
          sx={{ flex: 1, minWidth: 220 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                {searchLoading ? <CircularProgress size={16} /> : <SearchIcon fontSize="small" />}
              </InputAdornment>
            ),
            endAdornment: searchInput ? (
              <InputAdornment position="end">
                <IconButton size="small" onClick={clearSearch}>
                  <ClearIcon fontSize="small" />
                </IconButton>
              </InputAdornment>
            ) : null
          }}
        />

        {/* Action buttons — only in tree mode */}
        {!isSearchMode && (
          <Stack direction="row" spacing={1} flexWrap="wrap">
            {/* Specialty filter */}
            {specialties.length > 0 && (
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <InputLabel id="specialty-filter-label">تخصص</InputLabel>
                <Select
                  labelId="specialty-filter-label"
                  value={selectedSpecialty}
                  label="تخصص"
                  onChange={(e) => {
                    setSelectedSpecialty(e.target.value);
                    setExpandedIds(new Set());
                  }}
                >
                  <MenuItem value="">الكل</MenuItem>
                  {specialties.map((sp) => (
                    <MenuItem key={sp.code} value={sp.code}>
                      {sp.nameAr}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}

            <Button
              size="small"
              variant={masterOnly ? 'contained' : 'outlined'}
              color="warning"
              startIcon={<FilterListIcon />}
              onClick={() => setMasterOnly((v) => !v)}
            >
              {masterOnly ? 'الكل' : 'الرئيسية فقط'}
            </Button>

            <ButtonGroup size="small" variant="outlined">
              <Button startIcon={<UnfoldMoreIcon />} onClick={expandAll}>
                توسيع الكل
              </Button>
              <Button startIcon={<UnfoldLessIcon />} onClick={collapseAll}>
                طي الكل
              </Button>
            </ButtonGroup>
          </Stack>
        )}
      </Stack>

      {/* Error */}
      {treeError && !isSearchMode && (
        <Alert severity="error" sx={{ mb: 2 }}>
          تعذّر تحميل الكتالوج. يرجى المحاولة لاحقاً.
        </Alert>
      )}

      {/* Search mode */}
      {isSearchMode && (
        <Box>
          {searchLoading ? (
            <Stack alignItems="center" py={4}>
              <CircularProgress size={28} />
            </Stack>
          ) : searchResults.length === 0 ? (
            <Alert severity="info">لا توجد نتائج مطابقة للبحث "{searchQuery}"</Alert>
          ) : (
            <Box
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                overflow: 'hidden'
              }}
            >
              <Box sx={{ px: 1.5, py: 1, bgcolor: 'grey.50', borderBottom: '1px solid', borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary">
                  {searchResults.length} نتيجة{searchResults.length === 50 ? ' (الحد الأقصى)' : ''}
                </Typography>
              </Box>
              <List dense disablePadding>
                {searchResults.map((item) => (
                  <SearchResultRow key={item.id} item={item} />
                ))}
              </List>
            </Box>
          )}
        </Box>
      )}

      {/* Tree mode */}
      {!isSearchMode && (
        <>
          {treeLoading ? (
            <Stack spacing={1}>
              {[...Array(5)].map((_, i) => (
                <Box
                  key={i}
                  sx={{ height: 48, bgcolor: 'grey.100', borderRadius: 2, animation: 'pulse 1.5s infinite' }}
                />
              ))}
            </Stack>
          ) : normalizedTree.length === 0 ? (
            <Alert severity="info">
              {masterOnly ? 'لا توجد خدمات رئيسية مصنّفة حالياً.' : 'الكتالوج فارغ. ستظهر التصنيفات والخدمات بعد إدخال البيانات.'}
            </Alert>
          ) : (
            <Box>
              <Box
                sx={{
                  mb: 2,
                  p: 1.5,
                  border: '1px dashed',
                  borderColor: draggedServiceId ? 'primary.main' : 'divider',
                  borderRadius: 2,
                  bgcolor: 'background.paper'
                }}
              >
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  خدمات غير مصنّفة (اختر عدة خدمات ثم اربطها دفعة واحدة)
                </Typography>

                {uncategorizedServices.length > 0 && (
                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mb: 1.5 }} alignItems={{ xs: 'stretch', md: 'center' }}>
                    <FormControl size="small" sx={{ minWidth: { xs: '100%', md: 260 } }}>
                      <InputLabel>تصنيف الربط</InputLabel>
                      <Select
                        value={selectedTargetCategoryId}
                        label="تصنيف الربط"
                        onChange={(e) => setSelectedTargetCategoryId(e.target.value)}
                      >
                        {allCategories.map((cat) => (
                          <MenuItem key={cat.id} value={cat.id}>
                            {cat.code} - {cat.nameAr || cat.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>

                    <Button size="small" variant="outlined" onClick={selectAllUncategorized}>
                      تحديد الكل
                    </Button>
                    <Button size="small" variant="outlined" onClick={clearUncategorizedSelection}>
                      إلغاء التحديد
                    </Button>
                    <Button
                      size="small"
                      variant="contained"
                      onClick={handleBulkAssignSelected}
                      disabled={!selectedTargetCategoryId || selectedServiceIds.length === 0 || bulkAssigning}
                    >
                      {bulkAssigning ? 'جارٍ الربط...' : `ربط المحدد (${selectedServiceIds.length})`}
                    </Button>
                  </Stack>
                )}

                {uncategorizedServices.length === 0 ? (
                  <Typography variant="caption" color="text.secondary">
                    لا توجد خدمات غير مصنفة حالياً.
                  </Typography>
                ) : (
                  <List dense disablePadding>
                    {uncategorizedServices.map((svc) => (
                      <ServiceRow
                        key={svc.id}
                        service={svc}
                        selected={selectedServiceIds.includes(svc.id)}
                        onClick={() => toggleUncategorizedSelection(svc.id)}
                        draggable
                        onDragStart={(id) => {
                          setDraggedServiceId(id);
                          setSelectedServiceIds([id]);
                        }}
                      />
                    ))}
                  </List>
                )}
              </Box>

              {normalizedTree.map((category) => (
                <CategoryAccordion
                  key={category.categoryId}
                  category={category}
                  expanded={expandedIds.has(category.categoryId)}
                  onToggle={() => toggleCategory(category.categoryId)}
                  onDropService={handleServiceDrop}
                  onDragServiceStart={setDraggedServiceId}
                  selectedServiceCount={selectedServiceIds.length}
                  onAssignSelected={(categoryId) => {
                    setSelectedTargetCategoryId(String(categoryId));
                    if (selectedServiceIds.length > 0) {
                      assignServicesToCategory(selectedServiceIds, categoryId);
                    }
                  }}
                  onCreateSubcategory={(categoryId) => navigate(`/medical-categories/add?parentId=${categoryId}`)}
                />
              ))}
            </Box>
          )}
        </>
      )}
    </Box>
  );
}
