import { useCallback, useEffect, useMemo, useState } from 'react';
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
  Drawer,
  Grid,
  IconButton,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import PsychologyAltIcon from '@mui/icons-material/PsychologyAlt';
import SyncAltIcon from '@mui/icons-material/SyncAlt';
import CloseIcon from '@mui/icons-material/Close';
import VisibilityIcon from '@mui/icons-material/Visibility';
import medicalDictionaryService from 'services/api/medical-dictionary.service';
import { getAllMedicalCategories } from 'services/api/medical-categories.service';

const statusColor = {
  PENDING: 'warning',
  APPROVED: 'success',
  MERGED: 'success',
  DRAFT: 'warning',
  DISABLED: 'default',
  REJECTED: 'error'
};

const getItems = (page) => page?.content || page?.items || page?.data || [];
const formatDateTime = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ar-LY');
};

export default function MedicalDictionaryPage() {
  const [query, setQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [entriesPage, setEntriesPage] = useState(null);
  const [matchText, setMatchText] = useState('');
  const [matches, setMatches] = useState([]);
  const [matching, setMatching] = useState(false);
  const [error, setError] = useState('');
  const [suggestionsPage, setSuggestionsPage] = useState(null);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
  const [changeLogPage, setChangeLogPage] = useState(null);
  const [changeLogLoading, setChangeLogLoading] = useState(false);
  const [suggestionCategoryOverrides, setSuggestionCategoryOverrides] = useState({});
  const [categories, setCategories] = useState([]);
  const [synonymForms, setSynonymForms] = useState({});
  const [savingSynonymId, setSavingSynonymId] = useState(null);
  const [synonymDrawerEntry, setSynonymDrawerEntry] = useState(null);
  const [synonymsPage, setSynonymsPage] = useState(null);
  const [synonymsLoading, setSynonymsLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('dictionary');
  const [synonymSearchQuery, setSynonymSearchQuery] = useState('');
  const [synonymSearchPage, setSynonymSearchPage] = useState(null);
  const [synonymSearchLoading, setSynonymSearchLoading] = useState(false);
  const [createForm, setCreateForm] = useState({
    canonicalName: '',
    medicalCategoryId: '',
    status: 'APPROVED',
    defaultConfidence: 85
  });

  const entries = useMemo(() => getItems(entriesPage), [entriesPage]);
  const suggestions = useMemo(() => getItems(suggestionsPage), [suggestionsPage]);
  const changeLog = useMemo(() => getItems(changeLogPage), [changeLogPage]);
  const drawerSynonyms = useMemo(() => getItems(synonymsPage), [synonymsPage]);
  const synonymSearchResults = useMemo(() => getItems(synonymSearchPage), [synonymSearchPage]);
  const entriesTotal = entriesPage?.total || entriesPage?.totalElements || entries.length;

  const loadEntries = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await medicalDictionaryService.searchDictionaryEntries({ query: appliedQuery, page: 0, size: 25 });
      setEntriesPage(result);
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر تحميل القاموس الطبي');
    } finally {
      setLoading(false);
    }
  }, [appliedQuery]);

  const loadSuggestions = useCallback(async () => {
    setSuggestionsLoading(true);
    try {
      const result = await medicalDictionaryService.listDictionarySuggestions({ status: 'PENDING', page: 0, size: 20 });
      setSuggestionsPage(result);
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر تحميل اقتراحات القاموس');
    } finally {
      setSuggestionsLoading(false);
    }
  }, []);

  const loadChangeLog = useCallback(async () => {
    setChangeLogLoading(true);
    try {
      const result = await medicalDictionaryService.listDictionarySuggestions({ page: 0, size: 100 });
      setChangeLogPage(result);
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر تحميل سجل تغييرات القاموس');
    } finally {
      setChangeLogLoading(false);
    }
  }, []);

  const loadCategories = useCallback(async () => {
    try {
      const result = await getAllMedicalCategories();
      setCategories(Array.isArray(result) ? result : []);
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر تحميل التصنيفات الطبية');
    }
  }, []);

  useEffect(() => {
    loadEntries();
    loadSuggestions();
    loadChangeLog();
    loadCategories();
  }, [loadEntries, loadSuggestions, loadChangeLog, loadCategories]);

  const handleSearch = () => setAppliedQuery(query.trim());

  const handleApproveSuggestion = async (suggestion) => {
    setError('');
    try {
      const overrideCategoryId = suggestionCategoryOverrides[suggestion.id];
      const categoryChanged = overrideCategoryId && String(overrideCategoryId) !== String(suggestion.suggestedCategoryId || '');
      await medicalDictionaryService.approveDictionarySuggestion(suggestion.id, {
        targetEntryId: categoryChanged ? null : suggestion.suggestedEntryId || null,
        targetCategoryId: Number(overrideCategoryId || suggestion.suggestedCategoryId || 0) || null,
        canonicalName: categoryChanged || !suggestion.suggestedEntryId ? suggestion.originalText : null,
        approveAsSynonym: !categoryChanged && Boolean(suggestion.suggestedEntryId),
        reviewNote: 'اعتماد من شاشة القاموس الطبي'
      });
      await loadSuggestions();
      await loadChangeLog();
      await loadEntries();
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر اعتماد الاقتراح');
    }
  };

  const handleRejectSuggestion = async (suggestion) => {
    setError('');
    try {
      await medicalDictionaryService.rejectDictionarySuggestion(suggestion.id, {
        reviewNote: 'رفض من شاشة القاموس الطبي'
      });
      await loadSuggestions();
      await loadChangeLog();
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر رفض الاقتراح');
    }
  };

  const handleCreateEntry = async () => {
    if (!createForm.canonicalName.trim() || !createForm.medicalCategoryId) {
      setError('أدخل الاسم الموحد واختر التصنيف الطبي');
      return;
    }
    setError('');
    try {
      await medicalDictionaryService.createDictionaryEntry({
        ...createForm,
        medicalCategoryId: Number(createForm.medicalCategoryId),
        defaultConfidence: Number(createForm.defaultConfidence)
      });
      setCreateForm({ canonicalName: '', medicalCategoryId: '', status: 'APPROVED', defaultConfidence: 85 });
      await loadEntries();
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر إنشاء سجل القاموس');
    }
  };

  const handleMatch = async () => {
    if (!matchText.trim()) return;
    setMatching(true);
    setError('');
    try {
      const result = await medicalDictionaryService.matchMedicalDictionary(matchText.trim());
      setMatches(Array.isArray(result) ? result : []);
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر اختبار المطابقة');
    } finally {
      setMatching(false);
    }
  };

  const handleSynonymSearch = async () => {
    if (!synonymSearchQuery.trim()) return;
    setSynonymSearchLoading(true);
    setError('');
    try {
      const result = await medicalDictionaryService.searchDictionarySynonyms({
        query: synonymSearchQuery.trim(),
        activeOnly: true,
        page: 0,
        size: 30
      });
      setSynonymSearchPage(result);
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر البحث في المرادفات');
    } finally {
      setSynonymSearchLoading(false);
    }
  };

  const handleRollbackLearnedSuggestion = async (suggestion) => {
    setError('');
    try {
      const result = await medicalDictionaryService.searchDictionarySynonyms({
        query: suggestion.originalText,
        activeOnly: true,
        page: 0,
        size: 20
      });
      const exactMatch = getItems(result).find(
        (item) => String(item.synonym || '').trim() === String(suggestion.originalText || '').trim()
      );

      if (!exactMatch?.synonymId) {
        setError('لا يمكن التراجع تلقائياً لأنني لم أجد مرادفاً نشطاً مطابقاً نصياً لهذا القرار. لم يتم تغيير أي قاعدة.');
        return;
      }

      await medicalDictionaryService.toggleDictionarySynonym(exactMatch.synonymId);
      await loadChangeLog();
      if (synonymSearchQuery.trim()) {
        await handleSynonymSearch();
      }
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر التراجع عن قاعدة القاموس المتعلمة');
    }
  };

  const handleSynonymFormChange = (entryId, value) => {
    setSynonymForms((prev) => ({ ...prev, [entryId]: value }));
  };

  const loadEntrySynonyms = useCallback(async (entry) => {
    if (!entry?.id) return;
    setSynonymDrawerEntry(entry);
    setSynonymsLoading(true);
    setError('');
    try {
      const result = await medicalDictionaryService.listDictionarySynonyms(entry.id, { page: 0, size: 100 });
      setSynonymsPage(result);
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر تحميل مرادفات السجل');
    } finally {
      setSynonymsLoading(false);
    }
  }, []);

  const handleAddSynonym = async (entryId) => {
    const synonym = (synonymForms[entryId] || '').trim();
    if (!synonym) return;

    setSavingSynonymId(entryId);
    setError('');
    try {
      await medicalDictionaryService.addDictionarySynonym(entryId, {
        synonym,
        synonymType: 'COMMON',
        language: 'ar',
        active: true
      });
      setSynonymForms((prev) => ({ ...prev, [entryId]: '' }));
      await loadEntries();
      if (synonymDrawerEntry?.id === entryId) {
        await loadEntrySynonyms(synonymDrawerEntry);
      }
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر إضافة المرادف');
    } finally {
      setSavingSynonymId(null);
    }
  };

  const handleToggleSynonym = async (synonym) => {
    setSavingSynonymId(synonym.entryId || synonym.id);
    setError('');
    try {
      await medicalDictionaryService.toggleDictionarySynonym(synonym.id);
      if (synonymDrawerEntry) {
        await loadEntrySynonyms(synonymDrawerEntry);
      }
    } catch (err) {
      setError(err?.response?.data?.message || 'تعذر تغيير حالة المرادف');
    } finally {
      setSavingSynonymId(null);
    }
  };

  return (
    <Box sx={{ p: 3 }} dir="rtl">
      <Stack spacing={3}>
        <Box>
          <Typography variant="h3" sx={{ fontWeight: 800 }}>
            القاموس الطبي
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            ذاكرة تصنيف داخلية تتعلم من قوائم الأسعار وتعديلات المراجعين. لا تعتمد قرارًا ماليًا وحدها.
          </Typography>
        </Box>

        {error && <Alert severity="error">{error}</Alert>}

        <Alert severity="info" icon={<PsychologyAltIcon />}>
          القاموس يقترح التصنيف التأميني فقط. التغطية، النسبة، السقوف، والمرفوض تبقى من اختصاص محرك التغطية.
        </Alert>

        <Card>
          <Tabs
            value={activeTab}
            onChange={(_, value) => setActiveTab(value)}
            variant="scrollable"
            scrollButtons="auto"
            sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}
          >
            <Tab value="dictionary" label={`القاموس (${entriesTotal})`} />
            <Tab value="synonyms" label="بحث المرادفات" />
            <Tab value="match" label="اختبار المطابقة" />
            <Tab value="suggestions" label={`اقتراحات المراجعة (${suggestions.length})`} />
            <Tab value="change-log" label={`سجل التغييرات (${changeLogPage?.total || changeLog.length})`} />
          </Tabs>
        </Card>

        {activeTab === 'dictionary' && (
          <Stack spacing={3}>
        <Card>
          <CardContent>
            <Typography variant="h5" sx={{ mb: 2, fontWeight: 700 }}>
              إضافة اسم موحد للقاموس
            </Typography>
            <Grid container spacing={1.5} alignItems="center">
              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="الاسم الموحد"
                  value={createForm.canonicalName}
                  onChange={(e) => setCreateForm((prev) => ({ ...prev, canonicalName: e.target.value }))}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <Autocomplete
                  fullWidth
                  options={categories}
                  value={categories.find((category) => String(category.id) === String(createForm.medicalCategoryId)) || null}
                  onChange={(event, category) => setCreateForm((prev) => ({ ...prev, medicalCategoryId: category?.id || '' }))}
                  getOptionLabel={(category) =>
                    category ? `${category.nameAr || category.name || ''}${category.code ? ` (${category.code})` : ''}` : ''
                  }
                  isOptionEqualToValue={(option, value) => String(option.id) === String(value.id)}
                  filterOptions={(options, state) => {
                    const query = state.inputValue.trim().toLowerCase();
                    if (!query) return options;
                    return options.filter((category) =>
                      [category.code, category.name, category.nameAr, category.nameEn]
                        .filter(Boolean)
                        .some((value) => String(value).toLowerCase().includes(query))
                    );
                  }}
                  renderInput={(params) => <TextField {...params} label="التصنيف الطبي" placeholder="ابحث بالاسم أو الكود..." />}
                />
              </Grid>
              <Grid item xs={6} md={2}>
                <TextField
                  fullWidth
                  type="number"
                  label="الثقة %"
                  value={createForm.defaultConfidence}
                  onChange={(e) => setCreateForm((prev) => ({ ...prev, defaultConfidence: e.target.value }))}
                  inputProps={{ min: 0, max: 100 }}
                />
              </Grid>
              <Grid item xs={6} md={2}>
                <Button fullWidth variant="contained" onClick={handleCreateEntry}>
                  إضافة
                </Button>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h5" sx={{ mb: 2, fontWeight: 700 }}>
              البحث في الاسم الموحد والمرادفات
            </Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
              <TextField
                fullWidth
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                placeholder="ابحث: MRI، رنين، تحليل CBC، علاج طبيعي..."
              />
              <Button variant="contained" startIcon={<SearchIcon />} onClick={handleSearch}>
                بحث
              </Button>
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Stack direction={{ xs: 'column', md: 'row' }} alignItems={{ xs: 'flex-start', md: 'center' }} justifyContent="space-between" spacing={2}>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  القاموس الموحد المحمّل
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  هذه بيانات القاموس المستوردة من أداة التصنيف. تظهر النتائج المفصلة مع المرادفات في جدول السجلات بالأسفل.
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Chip color="primary" label={`${entriesTotal} اسم موحد`} />
                <Chip color="success" variant="outlined" label={`${entries.length} ظاهر حالياً`} />
              </Stack>
            </Stack>

            <Divider sx={{ my: 2 }} />

            {loading ? (
              <Stack direction="row" spacing={1} alignItems="center">
                <CircularProgress size={20} />
                <Typography color="text.secondary">جاري تحميل بيانات القاموس...</Typography>
              </Stack>
            ) : entries.length === 0 ? (
              <Alert severity="warning">لا تظهر نتائج في الصفحة الحالية. جرّب البحث عن اسم خدمة أو تأكد من تطبيق seed القاموس.</Alert>
            ) : (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {entries.slice(0, 12).map((entry) => (
                  <Chip
                    key={entry.id}
                    icon={<LocalOfferIcon />}
                    color={entry.status === 'APPROVED' ? 'success' : 'default'}
                    variant="outlined"
                    label={`${entry.canonicalName} — ${entry.medicalCategoryCode}`}
                  />
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>

          </Stack>
        )}

        {activeTab === 'match' && (
          <Stack spacing={3}>
            <Card>
              <CardContent>
                <Typography variant="h5" sx={{ mb: 2, fontWeight: 700 }}>
                  اختبار مطابقة نص خدمة
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  اكتب اسم خدمة كما يرد من المرفق الطبي لترى أفضل تصنيف مقترح من القاموس. النتيجة هنا لا تعتمد أي مبلغ مالياً.
                </Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <TextField
                    fullWidth
                    value={matchText}
                    onChange={(e) => setMatchText(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleMatch()}
                    placeholder="مثال: رنين مغناطيسي للركبة"
                  />
                  <Button variant="contained" onClick={handleMatch} disabled={matching}>
                    {matching ? <CircularProgress size={20} /> : 'طابق'}
                  </Button>
                </Stack>
              </CardContent>
            </Card>

        {matches.length > 0 && (
          <Card>
            <CardContent>
              <Typography variant="h5" sx={{ mb: 2, fontWeight: 700 }}>
                نتائج المطابقة المقترحة
              </Typography>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {matches.map((m) => (
                  <Chip
                    key={`${m.entryId}-${m.matchType}-${m.matchedText}`}
                    color={m.confidence >= 90 ? 'success' : m.confidence >= 80 ? 'warning' : 'default'}
                    label={`${m.canonicalName} ← ${m.medicalCategoryCode} (${m.confidence}%)`}
                    variant="outlined"
                  />
                ))}
              </Stack>
            </CardContent>
          </Card>
        )}
          </Stack>
        )}

        {activeTab === 'synonyms' && (
          <Stack spacing={3}>
            <Card>
              <CardContent>
                <Typography variant="h5" sx={{ mb: 1, fontWeight: 800 }}>
                  البحث المباشر في المرادفات
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  استخدم هذا البحث عندما تريد معرفة إلى أي اسم موحد وتصنيف ينتمي نص خدمة وارد من قائمة أسعار.
                </Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <TextField
                    fullWidth
                    value={synonymSearchQuery}
                    onChange={(e) => setSynonymSearchQuery(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSynonymSearch()}
                    placeholder="ابحث عن مرادف أو اسم وارد من قائمة أسعار..."
                  />
                  <Button variant="contained" startIcon={<SearchIcon />} onClick={handleSynonymSearch} disabled={synonymSearchLoading}>
                    {synonymSearchLoading ? <CircularProgress size={20} /> : 'بحث'}
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
                  <Box>
                    <Typography variant="h5" sx={{ fontWeight: 800 }}>
                      نتائج المرادفات
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      النتيجة توضّح النص المطابق والاسم الموحد والتصنيف المقترح.
                    </Typography>
                  </Box>
                  <Chip color="primary" variant="outlined" label={`${synonymSearchPage?.total || synonymSearchResults.length} نتيجة`} />
                </Stack>

                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>المرادف المطابق</TableCell>
                        <TableCell>الاسم الموحد</TableCell>
                        <TableCell>التصنيف</TableCell>
                        <TableCell>النوع</TableCell>
                        <TableCell>الاستخدام</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {!synonymSearchLoading && synonymSearchResults.length === 0 && (
                        <TableRow>
                          <TableCell colSpan={5} align="center">
                            ابدأ بكتابة نص للبحث في المرادفات.
                          </TableCell>
                        </TableRow>
                      )}
                      {synonymSearchResults.map((item) => (
                        <TableRow key={item.synonymId} hover>
                          <TableCell sx={{ fontWeight: 700 }}>{item.synonym}</TableCell>
                          <TableCell>{item.canonicalName}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography>{item.medicalCategoryName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {item.medicalCategoryCode}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                              <Chip size="small" label={item.synonymType || 'COMMON'} variant="outlined" />
                              {item.learnedFromSource === 'CLAIM_REVIEW' && (
                                <Chip size="small" color="success" variant="outlined" label="تعلم من مطالبة" />
                              )}
                              {item.lifecycleStatus === 'LOCKED' && <Chip size="small" color="primary" label="مثبت" />}
                              {item.lifecycleStatus === 'DISABLED' && <Chip size="small" color="default" label="معطل" />}
                            </Stack>
                          </TableCell>
                          <TableCell>{item.usageCount || 0}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CardContent>
            </Card>
          </Stack>
        )}

        {activeTab === 'suggestions' && (
        <Card>
          <CardContent>
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  اقتراحات تحتاج مراجعة
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  هذه الاقتراحات تأتي لاحقًا من قوائم الأسعار أو تعديلات المراجعين. اعتمادها يحولها لذاكرة قابلة للبحث، لا لقرار مالي.
                </Typography>
              </Box>
              {suggestionsLoading && <CircularProgress size={22} />}
            </Stack>

            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>النص الأصلي</TableCell>
                    <TableCell>المصدر</TableCell>
                    <TableCell>التصنيف/السجل المقترح</TableCell>
                    <TableCell>الثقة</TableCell>
                    <TableCell>الإجراء</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {!suggestionsLoading && suggestions.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        لا توجد اقتراحات معلقة حاليًا.
                      </TableCell>
                    </TableRow>
                  )}
                  {suggestions.map((suggestion) => (
                    <TableRow key={suggestion.id} hover>
                      <TableCell sx={{ fontWeight: 700 }}>{suggestion.originalText}</TableCell>
                      <TableCell>{suggestion.source}</TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography>{suggestion.suggestedEntryName || suggestion.suggestedCategoryName || 'غير محدد'}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {suggestion.suggestedCategoryCode || '-'}
                          </Typography>
                          <Autocomplete
                            size="small"
                            options={categories}
                            value={
                              categories.find(
                                (category) =>
                                  String(category.id) ===
                                  String(suggestionCategoryOverrides[suggestion.id] || suggestion.suggestedCategoryId || '')
                              ) || null
                            }
                            onChange={(event, category) =>
                              setSuggestionCategoryOverrides((prev) => ({ ...prev, [suggestion.id]: category?.id || '' }))
                            }
                            getOptionLabel={(category) =>
                              category ? `${category.nameAr || category.name || ''}${category.code ? ` (${category.code})` : ''}` : ''
                            }
                            isOptionEqualToValue={(option, value) => String(option.id) === String(value.id)}
                            filterOptions={(options, state) => {
                              const query = state.inputValue.trim().toLowerCase();
                              if (!query) return options;
                              return options.filter((category) =>
                                [category.code, category.name, category.nameAr, category.nameEn]
                                  .filter(Boolean)
                                  .some((value) => String(value).toLowerCase().includes(query))
                              );
                            }}
                            renderInput={(params) => <TextField {...params} label="تغيير التصنيف قبل الاعتماد" />}
                          />
                        </Stack>
                      </TableCell>
                      <TableCell>{suggestion.confidence == null ? '-' : `${suggestion.confidence}%`}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1}>
                          <Button size="small" variant="contained" color="success" onClick={() => handleApproveSuggestion(suggestion)}>
                            اعتماد
                          </Button>
                          <Button size="small" variant="outlined" color="error" onClick={() => handleRejectSuggestion(suggestion)}>
                            رفض
                          </Button>
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

        {activeTab === 'change-log' && (
          <Card>
            <CardContent>
              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>
                    سجل تغييرات القاموس
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    هنا ترى كل ما دخل القاموس من المطالبات وقوائم الأسعار: المعلّق، المعتمد، المدموج، والمرفوض مع تواريخ المراجعة.
                  </Typography>
                </Box>
                {changeLogLoading && <CircularProgress size={22} />}
              </Stack>

              <Alert severity="warning" sx={{ mb: 2 }}>
                اعتماد أو رفض رئيس القسم هنا يؤثر على تعلم القاموس للمستقبل فقط. المطالبات المحفوظة لا يعاد فتح حسابها تلقائياً إلا إذا عدّلها المراجع وأعاد حفظها.
              </Alert>

              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>النص الأصلي</TableCell>
                      <TableCell>المصدر</TableCell>
                      <TableCell>الحالة</TableCell>
                      <TableCell>التصنيف/السجل</TableCell>
                      <TableCell>الثقة</TableCell>
                      <TableCell>تاريخ الإدخال</TableCell>
                      <TableCell>تاريخ القرار</TableCell>
                      <TableCell>الإجراء</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {!changeLogLoading && changeLog.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={8} align="center">
                          لا توجد تغييرات مسجلة حتى الآن.
                        </TableCell>
                      </TableRow>
                    )}
                    {changeLog.map((suggestion) => {
                      const isPending = suggestion.status === 'PENDING';
                      return (
                        <TableRow key={suggestion.id} hover>
                          <TableCell sx={{ fontWeight: 700 }}>{suggestion.originalText}</TableCell>
                          <TableCell>{suggestion.source || '-'}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color={statusColor[suggestion.status] || 'default'}
                              variant={isPending ? 'filled' : 'outlined'}
                              label={suggestion.status || '-'}
                            />
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography>{suggestion.suggestedEntryName || suggestion.suggestedCategoryName || 'غير محدد'}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {suggestion.suggestedCategoryCode || '-'}
                              </Typography>
                              {isPending && (
                                <Autocomplete
                                  size="small"
                                  options={categories}
                                  value={
                                    categories.find(
                                      (category) =>
                                        String(category.id) ===
                                        String(suggestionCategoryOverrides[suggestion.id] || suggestion.suggestedCategoryId || '')
                                    ) || null
                                  }
                                  onChange={(event, category) =>
                                    setSuggestionCategoryOverrides((prev) => ({ ...prev, [suggestion.id]: category?.id || '' }))
                                  }
                                  getOptionLabel={(category) =>
                                    category ? `${category.nameAr || category.name || ''}${category.code ? ` (${category.code})` : ''}` : ''
                                  }
                                  isOptionEqualToValue={(option, value) => String(option.id) === String(value.id)}
                                  filterOptions={(options, state) => {
                                    const query = state.inputValue.trim().toLowerCase();
                                    if (!query) return options;
                                    return options.filter((category) =>
                                      [category.code, category.name, category.nameAr, category.nameEn]
                                        .filter(Boolean)
                                        .some((value) => String(value).toLowerCase().includes(query))
                                    );
                                  }}
                                  renderInput={(params) => <TextField {...params} label="تغيير التصنيف" />}
                                />
                              )}
                            </Stack>
                          </TableCell>
                          <TableCell>{suggestion.confidence == null ? '-' : `${suggestion.confidence}%`}</TableCell>
                          <TableCell>{formatDateTime(suggestion.createdAt)}</TableCell>
                          <TableCell>{formatDateTime(suggestion.reviewedAt)}</TableCell>
                          <TableCell>
                            {isPending ? (
                              <Stack direction="row" spacing={1}>
                                <Button size="small" variant="contained" color="success" onClick={() => handleApproveSuggestion(suggestion)}>
                                  اعتماد دائم
                                </Button>
                                <Button size="small" variant="outlined" color="error" onClick={() => handleRejectSuggestion(suggestion)}>
                                  رفض
                                </Button>
                              </Stack>
                            ) : suggestion.status === 'MERGED' || suggestion.status === 'APPROVED' ? (
                              <Stack spacing={0.75}>
                                <Button size="small" variant="outlined" color="warning" onClick={() => handleRollbackLearnedSuggestion(suggestion)}>
                                  تراجع عن التعلم
                                </Button>
                                <Typography variant="caption" color="text.secondary">
                                  يوقف استخدام المرادف مستقبلاً ولا يغير مطالبات محفوظة.
                                </Typography>
                              </Stack>
                            ) : (
                              <Typography variant="caption" color="text.secondary">
                                لا يوجد إجراء مطلوب.
                              </Typography>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        )}

        {activeTab === 'dictionary' && (
        <Card>
          <CardContent>
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  السجلات المعتمدة والمقترحة
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  تظهر المرادفات أمامك لأنها جزء من قرار المطابقة وليست صندوقًا أسود.
                </Typography>
              </Box>
              {loading && <CircularProgress size={24} />}
            </Stack>

            <Divider sx={{ mb: 2 }} />

            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>الاسم الموحد</TableCell>
                    <TableCell>التصنيف</TableCell>
                    <TableCell>الحالة</TableCell>
                    <TableCell>الثقة</TableCell>
                    <TableCell>المرادفات</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {!loading && entries.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center">
                        لا توجد نتائج بعد. ابدأ بإضافة سجلات القاموس أو اربطه باستيراد قوائم الأسعار.
                      </TableCell>
                    </TableRow>
                  )}
                  {entries.map((entry) => (
                    <TableRow key={entry.id} hover>
                      <TableCell sx={{ fontWeight: 700 }}>{entry.canonicalName}</TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography>{entry.medicalCategoryName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {entry.medicalCategoryCode}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" color={statusColor[entry.status] || 'default'} label={entry.status} />
                      </TableCell>
                      <TableCell>{entry.defaultConfidence}%</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Chip size="small" icon={<LocalOfferIcon />} color="primary" variant="outlined" label={`${entry.synonymCount || 0} مرادف`} />
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<VisibilityIcon />}
                            onClick={() => loadEntrySynonyms(entry)}
                          >
                            إدارة
                          </Button>
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

        <Drawer
          anchor="left"
          open={Boolean(synonymDrawerEntry)}
          onClose={() => setSynonymDrawerEntry(null)}
          PaperProps={{ sx: { width: { xs: '100%', sm: 560 }, p: 2 } }}
        >
          <Stack spacing={2} dir="rtl">
            <Stack direction="row" alignItems="flex-start" justifyContent="space-between">
              <Box>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>
                  مرادفات الاسم الموحد
                </Typography>
                <Typography color="text.secondary">{synonymDrawerEntry?.canonicalName}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {synonymDrawerEntry?.medicalCategoryCode} — {synonymDrawerEntry?.medicalCategoryName}
                </Typography>
              </Box>
              <IconButton onClick={() => setSynonymDrawerEntry(null)}>
                <CloseIcon />
              </IconButton>
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <TextField
                size="small"
                fullWidth
                placeholder="أضف مرادفًا لهذا الاسم..."
                value={synonymForms[synonymDrawerEntry?.id] || ''}
                onChange={(e) => handleSynonymFormChange(synonymDrawerEntry?.id, e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAddSynonym(synonymDrawerEntry?.id)}
              />
              <Button
                size="small"
                variant="contained"
                startIcon={<SyncAltIcon />}
                disabled={!synonymDrawerEntry?.id || savingSynonymId === synonymDrawerEntry?.id}
                onClick={() => handleAddSynonym(synonymDrawerEntry?.id)}
                sx={{ minWidth: 140 }}
              >
                إضافة مرادف
              </Button>
            </Stack>

            <Divider />

            {synonymsLoading ? (
              <Stack direction="row" spacing={1} alignItems="center">
                <CircularProgress size={20} />
                <Typography color="text.secondary">جاري تحميل المرادفات...</Typography>
              </Stack>
            ) : drawerSynonyms.length === 0 ? (
              <Alert severity="info">لا توجد مرادفات لهذا السجل بعد.</Alert>
            ) : (
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {drawerSynonyms.map((syn) => (
                  <Tooltip key={syn.id} title={syn.active ? 'اضغط لتعطيل هذا المرادف مؤقتًا' : 'اضغط لإعادة تفعيل هذا المرادف'}>
                    <Chip
                      size="small"
                      icon={<LocalOfferIcon />}
                      onClick={() => handleToggleSynonym(syn)}
                      variant={syn.active ? 'outlined' : 'filled'}
                      color={syn.active ? 'primary' : 'default'}
                      label={`${syn.synonym}${syn.learnedFromSource === 'CLAIM_REVIEW' ? ' — تعلم من مطالبة' : ''}${
                        syn.lifecycleStatus === 'LOCKED' ? ' — مثبت' : syn.active ? '' : ' — معطل'
                      }`}
                      sx={{ cursor: 'pointer' }}
                    />
                  </Tooltip>
                ))}
              </Stack>
            )}
          </Stack>
        </Drawer>
      </Stack>
    </Box>
  );
}
