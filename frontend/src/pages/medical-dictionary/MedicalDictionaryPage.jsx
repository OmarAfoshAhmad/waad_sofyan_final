import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
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
  APPROVED: 'success',
  DRAFT: 'warning',
  DISABLED: 'default',
  REJECTED: 'error'
};

const getItems = (page) => page?.content || page?.items || page?.data || [];

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
  const [categories, setCategories] = useState([]);
  const [synonymForms, setSynonymForms] = useState({});
  const [savingSynonymId, setSavingSynonymId] = useState(null);
  const [synonymDrawerEntry, setSynonymDrawerEntry] = useState(null);
  const [synonymsPage, setSynonymsPage] = useState(null);
  const [synonymsLoading, setSynonymsLoading] = useState(false);
  const [createForm, setCreateForm] = useState({
    canonicalName: '',
    medicalCategoryId: '',
    status: 'APPROVED',
    defaultConfidence: 85
  });

  const entries = useMemo(() => getItems(entriesPage), [entriesPage]);
  const suggestions = useMemo(() => getItems(suggestionsPage), [suggestionsPage]);
  const drawerSynonyms = useMemo(() => getItems(synonymsPage), [synonymsPage]);
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
    loadCategories();
  }, [loadEntries, loadSuggestions, loadCategories]);

  const handleSearch = () => setAppliedQuery(query.trim());

  const handleApproveSuggestion = async (suggestion) => {
    setError('');
    try {
      await medicalDictionaryService.approveDictionarySuggestion(suggestion.id, {
        targetEntryId: suggestion.suggestedEntryId || null,
        targetCategoryId: suggestion.suggestedCategoryId || null,
        canonicalName: suggestion.suggestedEntryId ? null : suggestion.originalText,
        approveAsSynonym: Boolean(suggestion.suggestedEntryId),
        reviewNote: 'اعتماد من شاشة القاموس الطبي'
      });
      await loadSuggestions();
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
                <Select
                  fullWidth
                  displayEmpty
                  value={createForm.medicalCategoryId}
                  onChange={(e) => setCreateForm((prev) => ({ ...prev, medicalCategoryId: e.target.value }))}
                >
                  <MenuItem value="">اختر التصنيف الطبي</MenuItem>
                  {categories.map((category) => (
                    <MenuItem key={category.id} value={category.id}>
                      {category.name || category.nameAr} — {category.code}
                    </MenuItem>
                  ))}
                </Select>
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

        <Grid container spacing={2}>
          <Grid item xs={12} md={7}>
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
          </Grid>

          <Grid item xs={12} md={5}>
            <Card>
              <CardContent>
                <Typography variant="h5" sx={{ mb: 2, fontWeight: 700 }}>
                  اختبار مطابقة نص خدمة
                </Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <TextField
                    fullWidth
                    value={matchText}
                    onChange={(e) => setMatchText(e.target.value)}
                    placeholder="مثال: رنين مغناطيسي للركبة"
                  />
                  <Button variant="outlined" onClick={handleMatch} disabled={matching}>
                    {matching ? <CircularProgress size={20} /> : 'طابق'}
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

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
                      label={`${syn.synonym}${syn.active ? '' : ' — معطل'}`}
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
