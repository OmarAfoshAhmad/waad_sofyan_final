import React, { useState, useEffect } from 'react';
import {
  Box, Typography, Paper, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Button, IconButton, Dialog, DialogTitle, DialogContent,
  DialogActions, MenuItem, TextField, CircularProgress, Alert, Chip, Checkbox,
  InputAdornment, Grid
} from '@mui/material';
import { Check as CheckIcon, Edit as EditIcon, AutoAwesome as AutoAwesomeIcon, Search as SearchIcon } from '@mui/icons-material';
import axios from 'utils/axios';

const RELATIONSHIP_OPTIONS = [
  { value: 'SON', label: 'ابن (S)' },
  { value: 'DAUGHTER', label: 'ابنة (D)' },
  { value: 'WIFE', label: 'زوجة (W)' },
  { value: 'HUSBAND', label: 'زوج (H)' },
  { value: 'FATHER', label: 'أب (F)' },
  { value: 'MOTHER', label: 'أم (M)' }
];

const GENDER_OPTIONS = [
  { value: 'MALE', label: 'ذكر' },
  { value: 'FEMALE', label: 'أنثى' },
  { value: 'UNDEFINED', label: 'غير محدد' }
];

const KinshipMismatchChecker = () => {
  const [mismatches, setMismatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [filterRelation, setFilterRelation] = useState('ALL');
  const [filterGender, setFilterGender] = useState('ALL');

  // Selection
  const [selectedRows, setSelectedRows] = useState([]);

  // Dialog state
  const [openDialog, setOpenDialog] = useState(false);
  const [selectedMember, setSelectedMember] = useState(null); // null means bulk
  const [newRelation, setNewRelation] = useState('');
  const [newGender, setNewGender] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchMismatches();
  }, []);

  const fetchMismatches = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/system-settings/kinship-mismatches');
      setMismatches(res.data.data || []);
      setError(null);
      setSelectedRows([]);
    } catch (err) {
      setError('فشل في جلب الأخطاء: ' + (err.response?.data?.message || err.message));
    } finally {
      setLoading(false);
    }
  };

  const handleSelectAll = (event) => {
    if (event.target.checked) {
      setSelectedRows(filteredMismatches.map(m => m.id));
    } else {
      setSelectedRows([]);
    }
  };

  const handleSelectRow = (id) => {
    if (selectedRows.includes(id)) {
      setSelectedRows(selectedRows.filter(rowId => rowId !== id));
    } else {
      setSelectedRows([...selectedRows, id]);
    }
  };

  const handleOpenFix = (member) => {
    setSelectedMember(member);
    setNewRelation(member ? member.currentRelationship || '' : '');
    setNewGender(member ? member.currentGender || 'UNDEFINED' : 'UNDEFINED');
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setSelectedMember(null);
  };

  const handleFixSubmit = async () => {
    try {
      setSaving(true);
      if (selectedMember) {
        await axios.post(`/system-settings/kinship-mismatches/${selectedMember.id}/fix`, {
          newRelationship: newRelation,
          newGender: newGender
        });
        setMismatches(mismatches.filter(m => m.id !== selectedMember.id));
        setSelectedRows(selectedRows.filter(id => id !== selectedMember.id));
      } else {
        // Bulk Fix
        await axios.post('/system-settings/kinship-mismatches/bulk-fix', {
          memberIds: selectedRows,
          newRelationship: newRelation,
          newGender: newGender
        });
        setMismatches(mismatches.filter(m => !selectedRows.includes(m.id)));
        setSelectedRows([]);
      }
      handleCloseDialog();
    } catch (err) {
      alert('حدث خطأ أثناء حفظ التعديل');
    } finally {
      setSaving(false);
    }
  };

  const handleIgnore = async (id) => {
    if (!window.confirm('هل أنت متأكد من إبقاء البيانات وتجاهل الخطأ؟')) return;
    try {
      await axios.post(`/system-settings/kinship-mismatches/${id}/ignore`);
      setMismatches(mismatches.filter(m => m.id !== id));
      setSelectedRows(selectedRows.filter(rowId => rowId !== id));
    } catch (err) {
      alert('حدث خطأ أثناء تجاهل العضو');
    }
  };

  const handleBulkIgnore = async () => {
    if (selectedRows.length === 0) return;
    if (!window.confirm(`هل أنت متأكد من تجاهل الأخطاء لـ ${selectedRows.length} سجل(سجلات)؟`)) return;
    try {
      await axios.post('/system-settings/kinship-mismatches/bulk-ignore', selectedRows);
      setMismatches(mismatches.filter(m => !selectedRows.includes(m.id)));
      setSelectedRows([]);
    } catch (err) {
      alert('حدث خطأ أثناء التجاهل الجماعي');
    }
  };

  const translateGender = (gender) => {
    if (gender === 'MALE') return 'ذكر';
    if (gender === 'FEMALE') return 'أنثى';
    return 'غير محدد';
  };

  const translateRelation = (rel) => {
    const found = RELATIONSHIP_OPTIONS.find(o => o.value === rel);
    return found ? found.label : rel;
  };

  const filteredMismatches = mismatches.filter(m => {
    const matchName = m.fullName.toLowerCase().includes(searchQuery.toLowerCase());
    const matchRel = filterRelation === 'ALL' || m.currentRelationship === filterRelation;
    const matchGender = filterGender === 'ALL' || m.currentGender === filterGender || m.inferredGender === filterGender;
    return matchName && matchRel && matchGender;
  });

  if (loading) return <Box p={3} display="flex" justifyContent="center"><CircularProgress /></Box>;

  return (
    <Box p={3}>
      <Typography variant="h5" mb={3} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <AutoAwesomeIcon color="primary" />
        مراجعة أخطاء صلة القرابة والجنس
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              fullWidth
              size="small"
              placeholder="بحث بالاسم..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment>
              }}
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              fullWidth
              size="small"
              label="تصفية حسب صلة القرابة"
              value={filterRelation}
              onChange={(e) => setFilterRelation(e.target.value)}
            >
              <MenuItem value="ALL">الكل</MenuItem>
              {RELATIONSHIP_OPTIONS.map(opt => (
                <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              fullWidth
              size="small"
              label="تصفية حسب الجنس"
              value={filterGender}
              onChange={(e) => setFilterGender(e.target.value)}
            >
              <MenuItem value="ALL">الكل</MenuItem>
              <MenuItem value="MALE">ذكر</MenuItem>
              <MenuItem value="FEMALE">أنثى</MenuItem>
            </TextField>
          </Grid>
        </Grid>

        {filteredMismatches.length > 0 && (
          <Box mt={2} display="flex" justifyContent="flex-end">
            <Button
              variant="contained"
              color="primary"
              startIcon={<AutoAwesomeIcon />}
              onClick={() => {
                setSelectedRows(filteredMismatches.map(m => m.id));
                handleOpenFix(null);
              }}
            >
              إصلاح جميع نتائج التصفية دفعة واحدة ({filteredMismatches.length})
            </Button>
          </Box>
        )}
      </Paper>

      {selectedRows.length > 0 && (
        <Paper sx={{ p: 2, mb: 2, bgcolor: 'primary.light', color: 'primary.contrastText', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle1">
            تم تحديد {selectedRows.length} صفوف
          </Typography>
          <Box>
            <Button
              variant="contained"
              color="primary"
              startIcon={<EditIcon />}
              onClick={() => handleOpenFix(null)}
              sx={{ mr: 1, bgcolor: 'white', color: 'primary.main', '&:hover': { bgcolor: 'grey.100' } }}
            >
              إصلاح شامل
            </Button>
            <Button
              variant="contained"
              color="success"
              startIcon={<CheckIcon />}
              onClick={handleBulkIgnore}
            >
              تجاهل الأخطاء
            </Button>
          </Box>
        </Paper>
      )}

      <Paper>
        {mismatches.length === 0 ? (
          <Box p={4} textAlign="center">
            <Typography variant="h6" color="textSecondary">لا توجد أخطاء حالياً! بيانات النظام نظيفة المتطابقة.</Typography>
          </Box>
        ) : filteredMismatches.length === 0 ? (
          <Box p={4} textAlign="center">
            <Typography variant="h6" color="textSecondary">لا توجد نتائج مطابقة للبحث.</Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.100' }}>
                  <TableCell padding="checkbox">
                    <Checkbox
                      checked={selectedRows.length === filteredMismatches.length && filteredMismatches.length > 0}
                      indeterminate={selectedRows.length > 0 && selectedRows.length < filteredMismatches.length}
                      onChange={handleSelectAll}
                    />
                  </TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>اسم الموظف/التابع</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>الصلة المسجلة</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>الجنس المسجل</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>الجنس المتوقع</TableCell>
                  <TableCell sx={{ fontWeight: 'bold' }}>سبب التعارض</TableCell>
                  <TableCell sx={{ fontWeight: 'bold', width: '220px' }}>الإجراء</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredMismatches.map((row) => (
                  <TableRow key={row.id} hover selected={selectedRows.includes(row.id)}>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={selectedRows.includes(row.id)}
                        onChange={() => handleSelectRow(row.id)}
                      />
                    </TableCell>
                    <TableCell>{row.fullName}</TableCell>
                    <TableCell>
                      <Chip label={translateRelation(row.currentRelationship)} color="warning" size="small" />
                    </TableCell>
                    <TableCell>{translateGender(row.currentGender)}</TableCell>
                    <TableCell>
                      <Chip 
                        label={translateGender(row.inferredGender)} 
                        color={row.inferredGender === 'MALE' ? 'info' : (row.inferredGender === 'FEMALE' ? 'secondary' : 'default')} 
                        size="small" 
                        variant="outlined" 
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="error.main">{row.reason}</Typography>
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        variant="contained"
                        color="primary"
                        startIcon={<EditIcon />}
                        onClick={() => handleOpenFix(row)}
                        sx={{ mr: 1 }}
                      >
                        إصلاح
                      </Button>
                      <Button
                        size="small"
                        variant="outlined"
                        color="success"
                        startIcon={<CheckIcon />}
                        onClick={() => handleIgnore(row.id)}
                      >
                        إبقاء
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {/* Fix Dialog */}
      <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="xs" fullWidth>
        <DialogTitle>{selectedMember ? 'تعديل بيانات العضو' : 'إصلاح شامل للسجلات المحددة'}</DialogTitle>
        <DialogContent dividers>
          <Box display="flex" flexDirection="column" gap={3} pt={1}>
            {selectedMember ? (
              <Typography variant="subtitle2">
                الاسم: {selectedMember.fullName}
              </Typography>
            ) : (
              <Alert severity="info">
                سيتم تطبيق هذه القيم الجديدة على جميع الصفوف المحددة وعددهم ({selectedRows.length}).
              </Alert>
            )}
            
            <TextField
              select
              fullWidth
              label="تحديث صلة القرابة"
              value={newRelation}
              onChange={(e) => setNewRelation(e.target.value)}
            >
              {RELATIONSHIP_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </TextField>

            <TextField
              select
              fullWidth
              label="تحديث الجنس"
              value={newGender}
              onChange={(e) => setNewGender(e.target.value)}
            >
              {GENDER_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </TextField>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog} color="inherit">إلغاء</Button>
          <Button onClick={handleFixSubmit} variant="contained" disabled={saving}>
            {saving ? 'جاري الحفظ...' : 'حفظ التعديلات'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default KinshipMismatchChecker;
