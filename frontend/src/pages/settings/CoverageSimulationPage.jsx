import React, { useState, useEffect } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, TextField, Button,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Autocomplete, Chip, CircularProgress, MenuItem, Select,
  FormControl, InputLabel, Radio, RadioGroup, FormControlLabel, Divider,
  Tabs, Tab, Alert, Tooltip, IconButton
} from '@mui/material';
import {
  PlayArrow, Download, WarningAmber, CheckCircle, Cancel, HelpOutline,
  UploadFile, CompareArrows, Edit, Healing
} from '@mui/icons-material';
import { useSnackbar } from 'notistack';

import providerContractsService from 'services/api/provider-contracts.service';
import benefitPoliciesService from 'services/api/benefit-policies.service';
import simulationService from 'services/api/simulation.service';
import { parseExcelPriceList } from 'utils/classification.utils';
import MedicalClassificationReviewDialog from './MedicalClassificationReviewDialog';

// --- DIALOGS (Skeletons for now, can be extracted later) ---
const CreateBenefitRuleDialog = ({ open, onClose, item, policyId }) => (
    // Placeholder for rule creation
    null
);
const FixClassificationDialog = ({ open, onClose, item }) => (
    // Placeholder for classification fix
    null
);
const ReviewExclusionDialog = ({ open, onClose, item }) => (
    // Placeholder for exclusion review
    null
);

const CoverageSimulationPage = () => {
  const { enqueueSnackbar } = useSnackbar();

  // Tabs
  const [activeTab, setActiveTab] = useState(0);

  // Selections
  const [contracts, setContracts] = useState([]);
  const [policies, setPolicies] = useState([]);
  const [selectedContract, setSelectedContract] = useState(null);
  const [selectedPolicy, setSelectedPolicy] = useState(null);
  const [encounterType, setEncounterType] = useState('ALL');
  const [simulationMode, setSimulationMode] = useState('CONTRACT');
  const [rawFile, setRawFile] = useState(null);
  const [parsedItems, setParsedItems] = useState([]);
  
  // Raw Preview State
  const [rawPage, setRawPage] = useState(0);
  const [rawRowsPerPage, setRawRowsPerPage] = useState(10);
  const [rawSearch, setRawSearch] = useState('');
  
  // Data State
  const [loading, setLoading] = useState(false);
  const [simulationResult, setSimulationResult] = useState(null);
  const [filterStatus, setFilterStatus] = useState('ALL');

  // Dialogs State
  const [dialogState, setDialogState] = useState({ type: null, item: null });

  useEffect(() => {
    fetchInitialData();
  }, []);

  const fetchInitialData = async () => {
    try {
      const contractsRes = await providerContractsService.getProviderContracts({ size: 100 });
      if (contractsRes?.content) setContracts(contractsRes.content);
      else if (contractsRes?.data?.content) setContracts(contractsRes.data.content);

      const policiesRes = await benefitPoliciesService.getBenefitPolicies({ size: 100 });
      if (policiesRes?.content) setPolicies(policiesRes.content);
      else if (policiesRes?.data?.content) setPolicies(policiesRes.data.content);
    } catch (error) {
      console.error('Error fetching initial data:', error);
      enqueueSnackbar('فشل في جلب البيانات الأولية', { variant: 'error' });
    }
  };

  const handleFileChange = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setRawFile(file);
    try {
      setLoading(true);
      const items = await parseExcelPriceList(file);
      setParsedItems(items);
      enqueueSnackbar(`تم استخراج وتصنيف ${items.length} خدمة بنجاح`, { variant: 'success' });
    } catch (error) {
      console.error('File parse error:', error);
      enqueueSnackbar('حدث خطأ أثناء قراءة وتصنيف الملف', { variant: 'error' });
      setRawFile(null);
      setParsedItems([]);
    } finally {
      setLoading(false);
    }
  };

  const handleRunSimulation = async () => {
    if (!selectedPolicy) {
      enqueueSnackbar('يرجى اختيار الوثيقة (Benefit Policy)', { variant: 'warning' });
      return;
    }
    if (simulationMode === 'CONTRACT' && !selectedContract) {
      enqueueSnackbar('يرجى اختيار العقد', { variant: 'warning' });
      return;
    }
    if (simulationMode === 'RAW' && parsedItems.length === 0) {
      enqueueSnackbar('يرجى رفع ملف أسعار صالح للخدمات', { variant: 'warning' });
      return;
    }

    setLoading(true);
    try {
      let res;
      if (simulationMode === 'CONTRACT') {
        res = await simulationService.runCoverageSimulation(selectedContract.id, selectedPolicy.id, encounterType, true);
      } else {
        res = await simulationService.runRawCoverageSimulation(selectedPolicy.id, encounterType, parsedItems, true);
      }
      
      if (res && res.data) {
        setSimulationResult(res.data);
        enqueueSnackbar('اكتملت المحاكاة وتم حفظ نسخة (Snapshot) بنجاح', { variant: 'success' });
        setActiveTab(1); // Switch to Results tab
      }
    } catch (error) {
      console.error('Simulation error:', error);
      enqueueSnackbar('حدث خطأ أثناء المحاكاة', { variant: 'error' });
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async () => {
    if (!simulationResult?.simulationId) return;
    try {
      const blob = await simulationService.downloadSimulationReport(simulationResult.simulationId);
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `Simulation_${simulationResult.simulationId}.xlsx`);
      document.body.appendChild(link);
      link.click();
    } catch (error) {
      enqueueSnackbar('فشل في تصدير التقرير', { variant: 'error' });
    }
  };

  const getStatusColor = (status) => {
    if (status.includes('COVERED')) return 'success';
    if (status.includes('EXCLUDED') || status === 'PRICE_ZERO' || status === 'INVALID_CATEGORY') return 'error';
    if (status.includes('REVIEW') || status === 'CONTEXT_MISMATCH') return 'warning';
    return 'default';
  };

  const getStatusIcon = (status) => {
    if (status.includes('COVERED')) return <CheckCircle fontSize="small" />;
    if (status.includes('EXCLUDED') || status === 'PRICE_ZERO' || status === 'INVALID_CATEGORY') return <Cancel fontSize="small" />;
    if (status.includes('REVIEW') || status === 'CONTEXT_MISMATCH') return <WarningAmber fontSize="small" />;
    return <HelpOutline fontSize="small" />;
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'COVERED_EXACT_RULE': return 'مغطاة (قاعدة دقيقة)';
      case 'COVERED_PARENT_RULE': return 'مغطاة (قاعدة أب)';
      case 'COVERED_DEFAULT': return 'مغطاة (الوثيقة العامة)';
      case 'EXCLUDED_CATEGORY': return 'مستثناة (الفئة)';
      case 'NO_BENEFIT_RULE': return 'لا توجد قاعدة';
      case 'INVALID_CATEGORY': return 'تصنيف غير صالح';
      case 'CONTEXT_MISMATCH': return 'تعارض السياق';
      case 'PRICE_ZERO': return 'السعر صفر';
      default: return status;
    }
  };

  const handleUpdateParsedItem = (id, field, value) => {
    setParsedItems(prev => prev.map(item => 
      item.id === id ? { ...item, [field]: value, isEdited: true } : item
    ));
  };

  const renderRawPreview = () => {
    if (simulationMode !== 'RAW' || parsedItems.length === 0) return null;

    const filteredRaw = parsedItems.filter(item => 
      item.serviceName.toLowerCase().includes(rawSearch.toLowerCase()) || 
      (item.mainCategory || '').toLowerCase().includes(rawSearch.toLowerCase())
    );

    const paginatedRaw = filteredRaw.slice(rawPage * rawRowsPerPage, rawPage * rawRowsPerPage + rawRowsPerPage);

    return (
      <Grid item xs={12}>
        <Card variant="outlined" sx={{ mt: 2, borderColor: 'primary.light' }}>
          <CardContent>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="subtitle1" color="primary" fontWeight="bold">
                معاينة نتائج تصنيف الملف المرفوع ({parsedItems.length} خدمة)
              </Typography>
              <TextField 
                size="small" 
                placeholder="بحث بالاسم أو التصنيف..." 
                value={rawSearch} 
                onChange={(e) => { setRawSearch(e.target.value); setRawPage(0); }}
                sx={{ width: 300 }}
              />
            </Box>
            <TableContainer sx={{ maxHeight: 400, width: '100%' }}>
              <Table size="small" stickyHeader sx={{ minWidth: 800, width: '100%', tableLayout: 'fixed' }}>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100', width: '35%' }}>اسم الخدمة</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100', width: '15%' }}>السعر</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100', width: '15%' }}>الدقة (CONFIDENCE)</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100', width: '17%' }}>التصنيف الرئيسي</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100', width: '18%' }}>التصنيف الفرعي</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {paginatedRaw.map((item) => (
                    <TableRow key={item.id} sx={{ bgcolor: item.isEdited ? 'action.hover' : 'inherit' }}>
                      <TableCell>{item.serviceName}</TableCell>
                      <TableCell>{item.contractPrice}</TableCell>
                      <TableCell>
                        {item.confidenceScore >= 0.8 ? (
                          <Chip label={`${(item.confidenceScore * 100).toFixed(0)}%`} size="small" color="success" />
                        ) : item.confidenceScore >= 0.5 ? (
                          <Chip label={`${(item.confidenceScore * 100).toFixed(0)}%`} size="small" color="warning" />
                        ) : (
                          <Chip label={`${(item.confidenceScore * 100).toFixed(0)}%`} size="small" color="error" />
                        )}
                      </TableCell>
                      <TableCell>
                        <Select 
                          size="small" 
                          value={item.mainCategory || 'عام'} 
                          onChange={(e) => handleUpdateParsedItem(item.id, 'mainCategory', e.target.value)}
                          fullWidth
                        >
                          {['إيواء', 'عيادات خارجية'].map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                          <MenuItem value="عام">عام</MenuItem>
                        </Select>
                      </TableCell>
                      <TableCell>
                        <TextField 
                          size="small" 
                          fullWidth
                          value={item.subCategory || ''} 
                          onChange={(e) => handleUpdateParsedItem(item.id, 'subCategory', e.target.value)}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                  {filteredRaw.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 3 }}>
                        لا توجد نتائج مطابقة للبحث
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            {/* Pagination Controls */}
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2, gap: 2, alignItems: 'center' }}>
               <Typography variant="body2">الصفحة: {rawPage + 1} من {Math.ceil(filteredRaw.length / rawRowsPerPage) || 1}</Typography>
               <Button disabled={rawPage === 0} onClick={() => setRawPage(p => p - 1)}>السابق</Button>
               <Button disabled={rawPage >= Math.ceil(filteredRaw.length / rawRowsPerPage) - 1} onClick={() => setRawPage(p => p + 1)}>التالي</Button>
            </Box>
          </CardContent>
        </Card>
      </Grid>
    );
  };

  const filteredItems = simulationResult?.items?.filter(item => {
    if (filterStatus === 'ALL') return true;
    if (filterStatus === 'COVERED') return item.coverageStatus?.includes('COVERED');
    if (filterStatus === 'EXCLUDED') return item.coverageStatus?.includes('EXCLUDED');
    if (filterStatus === 'ISSUES') return ['PRICE_ZERO', 'INVALID_CATEGORY', 'CONTEXT_MISMATCH'].includes(item.coverageStatus) || (!item.coverageStatus?.includes('COVERED') && !item.coverageStatus?.includes('EXCLUDED') && !item.coverageStatus?.includes('REVIEW') && !item.coverageStatus?.includes('LOW_CONF'));
    return item.coverageStatus === filterStatus || item.coverageStatus?.includes(filterStatus);
  }) || [];

  const counts = {
    ALL: simulationResult?.items?.length || 0,
    COVERED: simulationResult?.items?.filter(i => i.coverageStatus?.includes('COVERED')).length || 0,
    EXCLUDED: simulationResult?.items?.filter(i => i.coverageStatus?.includes('EXCLUDED')).length || 0,
    ISSUES: simulationResult?.items?.filter(i => ['PRICE_ZERO', 'INVALID_CATEGORY', 'CONTEXT_MISMATCH'].includes(i.coverageStatus) || (!i.coverageStatus?.includes('COVERED') && !i.coverageStatus?.includes('EXCLUDED') && !i.coverageStatus?.includes('REVIEW') && !i.coverageStatus?.includes('LOW_CONF'))).length || 0,
    REVIEW: simulationResult?.items?.filter(i => i.coverageStatus?.includes('REVIEW')).length || 0,
    LOW_CONF: simulationResult?.items?.filter(i => i.coverageStatus?.includes('LOW_CONF')).length || 0,
  };

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" fontWeight={700} color="primary.main" gutterBottom>
        أداة محاكاة واعتماد التغطية (Simulation Engine)
      </Typography>
      <Typography variant="subtitle1" color="text.secondary" sx={{ mb: 3 }}>
        قم بمحاكاة وتدقيق التغطية التأمينية لقوائم الأسعار قبل ترحيلها إلى النظام الفعلي.
      </Typography>

      <Paper sx={{ mb: 3 }}>
        <Tabs value={activeTab} onChange={(e, v) => setActiveTab(v)} indicatorColor="primary" textColor="primary" variant="fullWidth">
          <Tab label="1. التشغيل والإعدادات" />
          <Tab label="2. نتائج المحاكاة" disabled={!simulationResult} />
          <Tab label="3. الملخص والفجوات" disabled={!simulationResult} />
        </Tabs>
      </Paper>

      {/* TAB 0: RUN SIMULATION */}
      {activeTab === 0 && (
        <Card elevation={3} sx={{ borderRadius: 2 }}>
          <CardContent sx={{ p: 4 }}>
            <Grid container spacing={3}>
              <Grid item xs={12}>
                <FormControl component="fieldset">
                  <RadioGroup row value={simulationMode} onChange={(e) => setSimulationMode(e.target.value)}>
                    <FormControlLabel value="CONTRACT" control={<Radio />} label="محاكاة من عقد موجود في النظام" />
                    <FormControlLabel value="RAW" control={<Radio />} label="محاكاة من ملف أسعار جديد (Excel)" />
                  </RadioGroup>
                </FormControl>
              </Grid>

              {simulationMode === 'CONTRACT' && (
                <Grid item xs={12} md={3} sx={{ display: 'flex' }}>
                  <Autocomplete
                    sx={{ flexGrow: 1 }}
                    options={contracts}
                    getOptionLabel={(option) => `${option.provider?.name} - ${option.contractCode}`}
                    onChange={(e, val) => setSelectedContract(val)}
                    renderInput={(params) => <TextField {...params} label="اختر العقد الصحي" variant="outlined" sx={{ height: '56px' }} />}
                  />
                </Grid>
              )}

              {simulationMode === 'RAW' && (
                <Grid item xs={12} md={3} sx={{ display: 'flex' }}>
                  <Button variant="outlined" component="label" startIcon={<UploadFile />} sx={{ flexGrow: 1, height: '56px', display: 'flex', justifyContent: 'flex-start', px: 2 }}>
                    {rawFile ? rawFile.name : 'رفع ملف Excel'}
                    <input type="file" hidden accept=".xlsx, .xls" onChange={handleFileChange} />
                  </Button>
                </Grid>
              )}

              <Grid item xs={12} md={3} sx={{ display: 'flex' }}>
                <Autocomplete
                  sx={{ flexGrow: 1 }}
                  options={policies}
                  getOptionLabel={(option) => `${option.name} (${option.policyCode})`}
                  onChange={(e, val) => setSelectedPolicy(val)}
                  renderInput={(params) => <TextField {...params} label="اختر وثيقة التغطية" variant="outlined" sx={{ height: '56px' }} />}
                />
              </Grid>

              <Grid item xs={12} md={3} sx={{ display: 'flex' }}>
                <FormControl sx={{ flexGrow: 1 }}>
                  <InputLabel>نوع اللقاء</InputLabel>
                  <Select value={encounterType} onChange={(e) => setEncounterType(e.target.value)} label="نوع اللقاء" sx={{ height: '56px' }}>
                    <MenuItem value="ALL">الكل (All)</MenuItem>
                    <MenuItem value="OUTPATIENT">عيادات خارجية</MenuItem>
                    <MenuItem value="INPATIENT">إيواء</MenuItem>
                  </Select>
                </FormControl>
              </Grid>

              <Grid item xs={12} md={3} sx={{ display: 'flex' }}>
                <Button 
                  variant="contained" 
                  size="large" 
                  fullWidth
                  startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <PlayArrow />}
                  onClick={handleRunSimulation}
                  disabled={loading}
                  sx={{ height: '56px', borderRadius: 2 }}
                >
                  {loading ? 'جاري المحاكاة...' : 'بدء المحاكاة'}
                </Button>
              </Grid>

              <Grid item xs={12}>
                <Divider sx={{ my: 1 }} />
              </Grid>

              {renderRawPreview()}
            </Grid>
          </CardContent>
        </Card>
      )}

      {/* TAB 1: RESULTS TABLE */}
      {activeTab === 1 && simulationResult && (
        <Card elevation={3} sx={{ borderRadius: 2 }}>
          <CardContent>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
              <Box sx={{ display: 'flex', gap: 2 }}>
                <Chip label={`كل الخدمات (${counts.ALL})`} onClick={() => setFilterStatus('ALL')} color={filterStatus === 'ALL' ? 'primary' : 'default'} />
                <Chip label={`مغطاة (${counts.COVERED})`} onClick={() => setFilterStatus('COVERED')} color={filterStatus === 'COVERED' ? 'success' : 'default'} />
                <Chip label={`مستثناة (${counts.EXCLUDED})`} onClick={() => setFilterStatus('EXCLUDED')} color={filterStatus === 'EXCLUDED' ? 'error' : 'default'} />
                <Chip label={`مشاكل فنية (${counts.ISSUES})`} onClick={() => setFilterStatus('ISSUES')} color={filterStatus === 'ISSUES' ? 'warning' : 'default'} />
                <Chip label={`يتطلب مراجعة (${counts.REVIEW})`} onClick={() => setFilterStatus('REVIEW')} color={filterStatus === 'REVIEW' ? 'secondary' : 'default'} />
                <Chip label={`ثقة منخفضة (${counts.LOW_CONF})`} onClick={() => setFilterStatus('LOW_CONF')} color={filterStatus === 'LOW_CONF' ? 'error' : 'default'} />
              </Box>
              <Box>
                 <Button variant="outlined" startIcon={<Download />} onClick={handleExport}>
                   تصدير Excel Snapshot
                 </Button>
              </Box>
            </Box>

            <TableContainer component={Paper} sx={{ maxHeight: 600 }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>اسم الخدمة</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>التصنيف (فئة)</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>المعنى الطبي</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>التخصص/الجسم</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>مؤشر الثقة</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>السعر</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>حالة التغطية</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>الإجراء الموصى به</TableCell>
                    <TableCell sx={{ fontWeight: 'bold', bgcolor: 'grey.100' }}>إجراءات</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredItems.map((item, index) => (
                    <TableRow key={index} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                      <TableCell>
                        <Typography variant="body2" fontWeight="medium">{item.serviceName}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.providerServiceCode}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{item.insuranceCategoryName || item.sourceMainCategory}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.insuranceCategoryCode}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{item.medicalMeaningAr || '-'}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.procedureType}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{item.medicalSpecialty || '-'}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.bodySystem}</Typography>
                      </TableCell>
                      <TableCell>
                        {item.classificationConfidence !== undefined && (
                          <Chip 
                            label={`${Math.round(item.classificationConfidence * 100)}%`} 
                            size="small" 
                            color={item.classificationConfidence >= 0.9 ? 'success' : item.classificationConfidence >= 0.7 ? 'warning' : 'error'} 
                            variant="outlined" 
                          />
                        )}
                      </TableCell>
                      <TableCell>{item.requestedAmount} د.ل</TableCell>
                      <TableCell>
                        <Chip 
                          icon={getStatusIcon(item.coverageStatus)} 
                          label={getStatusLabel(item.coverageStatus)} 
                          color={getStatusColor(item.coverageStatus)} 
                          size="small" 
                          variant="outlined" 
                        />
                        {item.warnings?.length > 0 && (
                          <Tooltip title={item.warnings.join(', ')}>
                            <WarningAmber color="warning" fontSize="small" sx={{ ml: 1, verticalAlign: 'middle' }} />
                          </Tooltip>
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary">{item.recommendedAction}</Typography>
                      </TableCell>
                      <TableCell>
                        <IconButton size="small" title="مراجعة التصنيف الطبي" onClick={() => setDialogState({ type: 'REVIEW_MEDICAL', item })}>
                          <Healing fontSize="small" color={item.requiresReview ? 'error' : 'inherit'} />
                        </IconButton>
                        <IconButton size="small" title="إصلاح التصنيف التأميني" onClick={() => setDialogState({ type: 'FIX_CLASS', item })}>
                          <Edit fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                  {filteredItems.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                        لا توجد خدمات تطابق الفلتر المحدد
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      )}

      {/* TAB 2: SUMMARY AND GAPS */}
      {activeTab === 2 && simulationResult && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={4}>
            <Card sx={{ bgcolor: 'success.light', color: 'success.contrastText', borderRadius: 2 }}>
              <CardContent>
                <Typography variant="h6">مغطاة بالكامل</Typography>
                <Typography variant="h3" fontWeight="bold">
                  {simulationResult.summary.coveredExact + simulationResult.summary.coveredByParent + simulationResult.summary.coveredDefault}
                </Typography>
                <Typography variant="body2">خدمة تم التعرف عليها وتغطيتها</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={4}>
            <Card sx={{ bgcolor: 'error.light', color: 'error.contrastText', borderRadius: 2 }}>
              <CardContent>
                <Typography variant="h6">مستثناة</Typography>
                <Typography variant="h3" fontWeight="bold">{simulationResult.summary.excludedCategory}</Typography>
                <Typography variant="body2">مستثناة بناءً على الوثيقة</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={4}>
            <Card sx={{ bgcolor: 'warning.light', color: 'warning.contrastText', borderRadius: 2 }}>
              <CardContent>
                <Typography variant="h6">فجوات (Gaps)</Typography>
                <Typography variant="h3" fontWeight="bold">
                  {simulationResult.summary.noBenefitRule + simulationResult.summary.invalidCategory + simulationResult.summary.contextMismatch}
                </Typography>
                <Typography variant="body2">تحتاج تدخّل يدوي</Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12}>
            <Card elevation={2} sx={{ borderRadius: 2 }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>تفاصيل الفجوات التقنية (Technical Gaps)</Typography>
                <Divider sx={{ mb: 2 }} />
                <Grid container spacing={2}>
                  <Grid item xs={6} md={3}>
                    <Alert severity="info">لا توجد قاعدة تغطية: <strong>{simulationResult.summary.noBenefitRule}</strong></Alert>
                  </Grid>
                  <Grid item xs={6} md={3}>
                    <Alert severity="error">تصنيف غير صالح: <strong>{simulationResult.summary.invalidCategory}</strong></Alert>
                  </Grid>
                  <Grid item xs={6} md={3}>
                    <Alert severity="warning">تعارض سياق (داخلي/خارجي): <strong>{simulationResult.summary.contextMismatch}</strong></Alert>
                  </Grid>
                  <Grid item xs={6} md={3}>
                    <Alert severity="error">السعر صفر: <strong>{simulationResult.summary.priceZero}</strong></Alert>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Render Dialogs */}
      {dialogState.type === 'ADD_RULE' && (
        <CreateBenefitRuleDialog open={true} onClose={() => setDialogState({ type: null, item: null })} item={dialogState.item} policyId={selectedPolicy?.id} />
      )}
      {dialogState.type === 'FIX_CLASS' && (
        <ReviewExclusionDialog 
          open={dialogState.type === 'REVIEW_EXCLUSION'}
          onClose={() => setDialogState({ type: null, item: null })}
          item={dialogState.item}
        />
      )}

      <MedicalClassificationReviewDialog
        open={dialogState.type === 'REVIEW_MEDICAL'}
        onClose={() => setDialogState({ type: null, item: null })}
        item={dialogState.item}
        onSave={(updatedItem) => {
            // Note: Since this is purely frontend demonstration for now, we'll just log or show a snackbar.
            // Ideally, this calls POST /api/medical-classification/approve or /rules.
            enqueueSnackbar('تم حفظ تصحيح التصنيف الطبي وسيعاد احتساب التغطية قريباً', { variant: 'info' });
            setDialogState({ type: null, item: null });
        }}
      />
    </Box>
  );
};

export default CoverageSimulationPage;
