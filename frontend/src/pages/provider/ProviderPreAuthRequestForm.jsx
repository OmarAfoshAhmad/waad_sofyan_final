import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Stepper,
  Step,
  StepLabel,
  Button,
  Typography,
  TextField,
  Grid,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Divider,
  Alert,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  CircularProgress,
  Chip,
  Autocomplete
} from '@mui/material';
import { useNavigate, useParams } from 'react-router-dom';
import DeleteIcon from '@mui/icons-material/Delete';
import AddCircleIcon from '@mui/icons-material/AddCircle';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import SearchIcon from '@mui/icons-material/Search';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import MainCard from 'components/MainCard';
import { useSnackbar } from 'notistack';
import { providerApi } from 'services/providerService';
import preApprovalsService from 'services/api/pre-approvals.service';
import { visitsService } from 'services/api/visits.service';
import providerContractsService from 'services/api/provider-contracts.service';

const steps = ['بيانات المستفيد والزيارة', 'البيانات السريرية', 'الخدمات والأسعار', 'المراجعة والإرسال'];

const ProviderPreAuthRequestForm = () => {
  const [activeStep, setActiveStep] = useState(0);
  const navigate = useNavigate();
  const { visitId } = useParams();
  const { enqueueSnackbar } = useSnackbar();
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Member search state
  const [member, setMember] = useState(null);
  const [memberLoading, setMemberLoading] = useState(true);
  const [memberError, setMemberError] = useState('');

  // Clinical state
  const [clinical, setClinical] = useState({
    chiefComplaint: '',
    diagnosis: '',
    treatmentPlan: '',
    notes: ''
  });

  // Services state
  const [lines, setLines] = useState([]);
  const [newLine, setNewLine] = useState({ medicalServiceId: '', code: '', name: '', price: '' });
  const [servicesCatalog, setServicesCatalog] = useState([]);

  // Attachments
  const [attachments, setAttachments] = useState([]);

  // ─── Fetch Visit Details ──────────────────────────────────────────────
  useEffect(() => {
    const fetchVisitDetails = async () => {
      if (!visitId) {
        setMemberError('معرف الزيارة غير متوفر. يرجى البدء من شاشة الزيارات.');
        setMemberLoading(false);
        return;
      }
      try {
        setMemberLoading(true);
        const data = await visitsService.getById(visitId);
        setMember({
          visitId: data.id,
          memberId: data.memberId,
          memberName: data.memberName,
          cardNumber: data.memberNumber,
          employerName: data.employerName,
          status: 'ACTIVE',
          coverageEndDate: 'حسب العقد',
          providerId: data.providerId
        });
      } catch (err) {
        setMemberError(err?.response?.data?.message || err?.message || 'خطأ في جلب بيانات الزيارة');
      } finally {
        setMemberLoading(false);
      }
    };
    fetchVisitDetails();
  }, [visitId]);

  // ─── Fetch Provider Catalog ──────────────────────────────────────────────
  useEffect(() => {
    if (member?.providerId) {
      // NOTE: Here we should ideally fetch the specific provider contract services.
      // If such endpoint doesn't exist yet, we will fetch medical services globally for now.
      const fetchCatalog = async () => {
        try {
          const res = await import('services/api/medical-services.service').then((m) =>
            m.medicalServicesService.getPaginated({ size: 1000 })
          );
          setServicesCatalog(res.items || res.content || res.data || []);
        } catch (err) {
          console.error('Failed to fetch services catalog:', err);
        }
      };
      fetchCatalog();
    }
  }, [member?.providerId]);

  // ─── Lines management ──────────────────────────────────────────────────────
  const addLine = () => {
    if (!newLine.medicalServiceId) {
      enqueueSnackbar('يرجى اختيار الخدمة الطبية', { variant: 'warning' });
      return;
    }
    setLines((prev) => [
      ...prev,
      {
        id: Date.now(),
        medicalServiceId: newLine.medicalServiceId,
        code: newLine.code,
        name: newLine.name,
        contractPrice: null, // يُحدَّد من قِبَل المراجع عند المقارنة بالعقد
        manualPrice: newLine.price,
        overrideReason: ''
      }
    ]);
    setNewLine({ medicalServiceId: '', code: '', name: '', price: '' });
  };

  const removeLine = (id) => setLines((prev) => prev.filter((l) => l.id !== id));

  const updateLine = (id, field, value) => {
    setLines((prev) => prev.map((l) => (l.id === id ? { ...l, [field]: value } : l)));
  };

  const calculateStatus = (line) => {
    if (line.isUnlisted) return { status: 'UNLISTED', percent: 0 };
    if (!line.manualPrice || line.manualPrice === '') return { status: 'MATCH_CONTRACT', percent: 0 };
    const manual = parseFloat(line.manualPrice);
    const contract = parseFloat(line.contractPrice);
    if (isNaN(manual)) return { status: 'MISSING_PRICE', percent: 0 };
    if (manual === contract) return { status: 'MATCH_CONTRACT', percent: 0 };
    const variance = ((manual - contract) / contract) * 100;
    const percent = variance.toFixed(1);
    if (manual < contract) return { status: 'BELOW_CONTRACT', percent };
    if (variance >= 25) return { status: 'CRITICAL_VARIANCE', percent };
    if (variance >= 10) return { status: 'HIGH_VARIANCE', percent };
    return { status: 'ABOVE_CONTRACT', percent };
  };

  // ─── Submission ────────────────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (!member) {
      enqueueSnackbar('يجب اختيار مستفيد أولاً', { variant: 'error' });
      return;
    }
    if (!clinical.diagnosis.trim()) {
      enqueueSnackbar('التشخيص مطلوب', { variant: 'error' });
      return;
    }
    if (lines.length === 0) {
      enqueueSnackbar('يجب إضافة خدمة واحدة على الأقل', { variant: 'error' });
      return;
    }

    setIsSubmitting(true);
    try {
      // Build per-service pre-auth requests using the real API endpoint
      const memberId = member?.memberId || member?.id;
      const visitId = member?.visitId;

      if (!visitId) {
        enqueueSnackbar('يجب إنشاء الموافقة المسبقة من زيارة مسجّلة في سجل الزيارات', { variant: 'warning', persist: true });
        setIsSubmitting(false);
        return;
      }

      // Submit one pre-auth per service line
      const results = await Promise.allSettled(
        lines.map((line) =>
          preApprovalsService.createFull({
            visitId,
            memberId,
            medicalServiceId: line.medicalServiceId,
            requestedAmount: parseFloat(line.manualPrice) || line.contractPrice || 0,
            diagnosis: clinical.diagnosis,
            treatmentPlan: clinical.treatmentPlan,
            chiefComplaint: clinical.chiefComplaint,
            notes: clinical.notes,
            overrideReason: line.overrideReason || null
          })
        )
      );

      const succeeded = results.filter((r) => r.status === 'fulfilled').length;
      const failed = results.filter((r) => r.status === 'rejected').length;

      if (succeeded > 0) {
        enqueueSnackbar(`تم إرسال ${succeeded} طلب${failed > 0 ? ` (فشل ${failed})` : ''} للمراجع الطبي بنجاح!`, {
          variant: succeeded === lines.length ? 'success' : 'warning'
        });
        setTimeout(() => navigate('/provider/pre-auth-inbox'), 1500);
      } else {
        enqueueSnackbar('فشل إرسال جميع الطلبات، يرجى المحاولة مرة أخرى', { variant: 'error' });
      }
    } catch (error) {
      console.error('Submit error:', error);
      enqueueSnackbar('حدث خطأ أثناء الإرسال', { variant: 'error' });
    } finally {
      setIsSubmitting(false);
    }
  };

  // ─── Step renders ──────────────────────────────────────────────────────────
  const renderStep1 = () => (
    <Box>
      <Typography variant="h6" mb={2}>
        بيانات المستفيد والزيارة
      </Typography>

      {memberLoading ? (
        <Box display="flex" justifyContent="center" p={3}>
          <CircularProgress />
        </Box>
      ) : memberError ? (
        <Alert severity="error">{memberError}</Alert>
      ) : member ? (
        <Card sx={{ mt: 1, border: '2px solid', borderColor: 'success.main', bgcolor: 'success.50' }}>
          <CardContent>
            <Box display="flex" alignItems="center" gap={1} mb={2}>
              <CheckCircleIcon color="success" />
              <Typography variant="subtitle1" color="success.main" fontWeight="bold">
                زيارة معتمدة ✓ (رقم الزيارة: {member.visitId})
              </Typography>
              <Chip
                label={member.status || 'ACTIVE'}
                color={member.status === 'ACTIVE' ? 'success' : 'error'}
                size="small"
                sx={{ ml: 'auto' }}
              />
            </Box>
            <Grid container spacing={2}>
              <Grid item xs={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  الاسم
                </Typography>
                <Typography fontWeight="bold">{member.memberName || member.name || '-'}</Typography>
              </Grid>
              <Grid item xs={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  رقم البطاقة
                </Typography>
                <Typography fontWeight="bold">{member.cardNumber || member.memberCardNumber || '-'}</Typography>
              </Grid>
              <Grid item xs={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  جهة العمل
                </Typography>
                <Typography fontWeight="bold">{member.employerName || member.employer || '-'}</Typography>
              </Grid>
              <Grid item xs={6} md={3}>
                <Typography variant="body2" color="textSecondary">
                  انتهاء التغطية
                </Typography>
                <Typography fontWeight="bold">{member.coverageEndDate || member.expiryDate || '-'}</Typography>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      ) : (
        <Alert severity="warning">لا يوجد بيانات للزيارة.</Alert>
      )}
    </Box>
  );

  const renderStep2 = () => (
    <Grid container spacing={3}>
      <Grid item xs={12}>
        <TextField
          fullWidth
          label="الشكوى الرئيسية (Chief Complaint)"
          multiline
          rows={2}
          required
          value={clinical.chiefComplaint}
          onChange={(e) => setClinical({ ...clinical, chiefComplaint: e.target.value })}
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          fullWidth
          label="التشخيص (Diagnosis / ICD Code)"
          multiline
          rows={2}
          required
          value={clinical.diagnosis}
          onChange={(e) => setClinical({ ...clinical, diagnosis: e.target.value })}
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          fullWidth
          label="الخطة العلاجية (Treatment Plan)"
          multiline
          rows={3}
          required
          value={clinical.treatmentPlan}
          onChange={(e) => setClinical({ ...clinical, treatmentPlan: e.target.value })}
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          fullWidth
          label="ملاحظات إضافية (اختياري)"
          multiline
          rows={2}
          value={clinical.notes}
          onChange={(e) => setClinical({ ...clinical, notes: e.target.value })}
        />
      </Grid>
    </Grid>
  );

  const renderStep3 = () => (
    <Box>
      {/* ── Add new service row ── */}
      <Card sx={{ mb: 3, p: 2, bgcolor: 'grey.50' }}>
        <Typography variant="subtitle2" mb={2} fontWeight="bold">
          إضافة خدمة طبية
        </Typography>
        <Grid container spacing={2} alignItems="flex-end">
          <Grid item xs={12} sm={8}>
            <Autocomplete
              options={servicesCatalog}
              getOptionLabel={(option) => `${option.serviceCode} - ${option.nameAr || option.nameEn || option.name}`}
              onChange={(e, newValue) => {
                if (newValue) {
                  setNewLine((p) => ({
                    ...p,
                    medicalServiceId: newValue.id,
                    code: newValue.serviceCode,
                    name: newValue.nameAr || newValue.nameEn || newValue.name,
                    price: newValue.price || '' // Assuming there's a base price or contract price available
                  }));
                } else {
                  setNewLine({ medicalServiceId: '', code: '', name: '', price: '' });
                }
              }}
              renderInput={(params) => <TextField {...params} label="اختر الخدمة الطبية من العقد" size="small" />}
            />
          </Grid>
          <Grid item xs={12} sm={2}>
            <TextField
              fullWidth
              size="small"
              type="number"
              label="المبلغ المطلوب (د.ل)"
              placeholder="0.00"
              value={newLine.price}
              onChange={(e) => setNewLine((p) => ({ ...p, price: e.target.value }))}
              inputProps={{ min: 0 }}
            />
          </Grid>
          <Grid item xs={12} sm={2}>
            <Button
              fullWidth
              variant="contained"
              size="medium"
              startIcon={<AddCircleIcon />}
              onClick={addLine}
              disabled={!newLine.medicalServiceId}
            >
              إضافة
            </Button>
          </Grid>
        </Grid>
      </Card>

      {/* ── Lines table ── */}
      {lines.length === 0 ? (
        <Alert severity="info">لم تتم إضافة أي خدمة بعد. أدخل بيانات الخدمة أعلاه ثم اضغط «إضافة».</Alert>
      ) : (
        <TableContainer component={Paper} sx={{ mb: 3 }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: 'primary.main' }}>
                <TableCell sx={{ color: 'white' }}>كود الخدمة</TableCell>
                <TableCell sx={{ color: 'white' }}>اسم الإجراء</TableCell>
                <TableCell sx={{ color: 'white' }} align="right">
                  المبلغ المطلوب
                </TableCell>
                <TableCell sx={{ color: 'white' }}>ملاحظة</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {lines.map((line, idx) => (
                <TableRow key={line.id} sx={{ bgcolor: idx % 2 === 0 ? 'inherit' : 'grey.50' }}>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace" fontWeight="bold">
                      {line.code || 'UNLISTED'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      fullWidth
                      value={line.name}
                      onChange={(e) => updateLine(line.id, 'name', e.target.value)}
                      placeholder="اسم الخدمة"
                      variant="standard"
                    />
                  </TableCell>
                  <TableCell align="right">
                    <TextField
                      size="small"
                      type="number"
                      value={line.manualPrice}
                      onChange={(e) => updateLine(line.id, 'manualPrice', e.target.value)}
                      sx={{ width: 110 }}
                      inputProps={{ min: 0, style: { textAlign: 'right' } }}
                      InputProps={{
                        endAdornment: (
                          <Typography variant="caption" color="textSecondary">
                            د.ل
                          </Typography>
                        )
                      }}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      fullWidth
                      value={line.overrideReason}
                      onChange={(e) => updateLine(line.id, 'overrideReason', e.target.value)}
                      placeholder="مبرر السعر (اختياري)"
                      variant="standard"
                    />
                  </TableCell>
                  <TableCell align="center">
                    <IconButton color="error" size="small" onClick={() => removeLine(line.id)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* ── Total ── */}
      {lines.length > 0 && (
        <Box display="flex" justifyContent="flex-end" mb={2}>
          <Chip
            label={`الإجمالي: ${lines.reduce((s, l) => s + (parseFloat(l.manualPrice) || 0), 0).toFixed(2)} د.ل`}
            color="primary"
            variant="outlined"
            sx={{ fontSize: '1rem', px: 2, py: 2.5 }}
          />
        </Box>
      )}

      {/* ── Attachments ── */}
      <Box>
        <Typography variant="subtitle2" mb={1} fontWeight="bold">
          المرفقات الداعمة (اختياري)
        </Typography>
        <Button variant="outlined" component="label" startIcon={<UploadFileIcon />} size="small">
          إرفاق ملفات
          <input
            type="file"
            hidden
            multiple
            onChange={(e) => {
              if (e.target.files) setAttachments((prev) => [...prev, ...Array.from(e.target.files)]);
            }}
          />
        </Button>
        {attachments.length > 0 && (
          <List dense sx={{ mt: 1, bgcolor: '#f5f5f5', borderRadius: 1 }}>
            {attachments.map((file, i) => (
              <ListItem key={i}>
                <ListItemIcon>
                  <UploadFileIcon color="primary" fontSize="small" />
                </ListItemIcon>
                <ListItemText primary={file.name} secondary={`${(file.size / 1024).toFixed(1)} KB`} />
                <ListItemSecondaryAction>
                  <IconButton
                    edge="end"
                    size="small"
                    color="error"
                    onClick={() => setAttachments((prev) => prev.filter((_, idx) => idx !== i))}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        )}
      </Box>
    </Box>
  );

  const renderStep4 = () => {
    const totalRequested = lines.reduce((acc, l) => acc + (parseFloat(l.manualPrice) || 0), 0);

    return (
      <Box>
        <Typography variant="h6" mb={2}>
          مراجعة الطلب قبل الإرسال
        </Typography>

        {/* Member summary */}
        {member && (
          <Card sx={{ mb: 2, border: '1px solid', borderColor: 'success.light', bgcolor: '#f5f8fa' }}>
            <CardContent sx={{ py: 1.5 }}>
              <Typography variant="subtitle2" color="textSecondary" gutterBottom>
                المستفيد
              </Typography>
              <Typography fontWeight="bold">{member.memberName || member.name || '-'}</Typography>
              <Typography variant="body2" color="textSecondary">
                {member.cardNumber || member.memberCardNumber || ''} | {member.employerName || ''}
              </Typography>
            </CardContent>
          </Card>
        )}

        {/* Lines summary */}
        <TableContainer component={Paper} sx={{ mb: 2 }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: 'grey.100' }}>
                <TableCell>#</TableCell>
                <TableCell>الكود</TableCell>
                <TableCell>الخدمة / الإجراء</TableCell>
                <TableCell align="right">المبلغ المطلوب</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {lines.map((l, i) => (
                <TableRow key={l.id}>
                  <TableCell sx={{ color: 'textSecondary' }}>{i + 1}</TableCell>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">
                      {l.code}
                    </Typography>
                  </TableCell>
                  <TableCell>{l.name}</TableCell>
                  <TableCell align="right">
                    <Typography fontWeight="bold" color="primary">
                      {parseFloat(l.manualPrice || 0).toFixed(2)} د.ل
                    </Typography>
                  </TableCell>
                </TableRow>
              ))}
              <TableRow sx={{ bgcolor: 'primary.50' }}>
                <TableCell colSpan={3} align="right">
                  <Typography fontWeight="bold">الإجمالي المطلوب:</Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography variant="h6" color="primary" fontWeight="bold">
                    {totalRequested.toFixed(2)} د.ل
                  </Typography>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>

        <Alert severity="info" sx={{ mb: 1 }}>
          سيتم إرسال <strong>{lines.length}</strong> طلب{lines.length > 1 ? ' منفصل' : ''} للمراجع الطبي. لن يُخصم من سقف المستفيد حتى تتم
          الموافقة الصريحة.
        </Alert>

        {!member?.visitId && (
          <Alert severity="warning">
            ⚠️ لا توجد زيارة مرتبطة بهذا المستفيد — لن يتم الإرسال. يرجى بدء الطلب من <strong>سجل الزيارات</strong>.
          </Alert>
        )}
      </Box>
    );
  };

  const getStepContent = (step) => {
    switch (step) {
      case 0:
        return renderStep1();
      case 1:
        return renderStep2();
      case 2:
        return renderStep3();
      case 3:
        return renderStep4();
      default:
        return null;
    }
  };

  const canProceed = () => {
    if (activeStep === 0) return !!member;
    if (activeStep === 1) return !!(clinical.diagnosis.trim() && clinical.treatmentPlan.trim());
    if (activeStep === 2) return lines.length > 0;
    return true;
  };

  return (
    <MainCard title="إنشاء طلب موافقة مسبقة">
      <Stepper activeStep={activeStep} alternativeLabel sx={{ mb: 4 }}>
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Box sx={{ minHeight: '400px' }}>{getStepContent(activeStep)}</Box>

      <Divider sx={{ my: 3 }} />

      <Box display="flex" justifyContent="space-between">
        <Button disabled={activeStep === 0 || isSubmitting} onClick={() => setActiveStep((prev) => prev - 1)} variant="outlined">
          السابق
        </Button>

        {activeStep === steps.length - 1 ? (
          <Button
            variant="contained"
            color="primary"
            disabled={isSubmitting || !member?.visitId}
            onClick={handleSubmit}
            startIcon={isSubmitting ? <CircularProgress size={18} /> : null}
          >
            {isSubmitting ? 'جاري الإرسال...' : `إرسال ${lines.length} طلب للاعتماد`}
          </Button>
        ) : (
          <Button variant="contained" onClick={() => setActiveStep((prev) => prev + 1)} disabled={!canProceed()}>
            التالي
          </Button>
        )}
      </Box>
    </MainCard>
  );
};

export default ProviderPreAuthRequestForm;
