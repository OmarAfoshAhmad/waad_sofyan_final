/**
 * Family Eligibility Check Page
 * Check eligibility for a member and view all family members' status
 */

import { useState, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Alert,
  CircularProgress,
  InputAdornment
} from '@mui/material';
import { Search as SearchIcon, CheckCircle as EligibleIcon, Cancel as IneligibleIcon, Person as PersonIcon } from '@mui/icons-material';
import { MemberAvatar } from 'components/tba';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs from 'dayjs';

import eligibilityService from 'services/eligibility/eligibility.service';

const FamilyEligibilityPage = () => {
  // State
  const [memberId, setMemberId] = useState('');
  const [serviceDate, setServiceDate] = useState(dayjs());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  // Handle check
  const handleCheck = useCallback(async () => {
    if (!memberId) {
      setError('يرجى إدخال رقم المشترك');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setResult(null);

      const response = await eligibilityService.checkFamilyEligibility(memberId, serviceDate?.format('YYYY-MM-DD'));

      setResult(response.data?.data || response.data);
    } catch (err) {
      console.error('Eligibility check failed:', err);
      setError(err.response?.data?.message || 'فشل فحص الأهلية');
    } finally {
      setLoading(false);
    }
  }, [memberId, serviceDate]);

  // Render eligibility chip
  const renderEligibilityChip = (eligible) => (
    <Chip
      icon={eligible ? <EligibleIcon /> : <IneligibleIcon />}
      label={eligible ? 'مؤهل' : 'غير مؤهل'}
      color={eligible ? 'success' : 'error'}
      size="small"
    />
  );

  // Render relationship type
  const getRelationshipLabel = (type) => {
    const labels = {
      PRINCIPAL: 'المشترك الأساسي',
      SPOUSE: 'الزوج/الزوجة',
      CHILD: 'ابن/ابنة',
      PARENT: 'الوالد/الوالدة',
      DEPENDENT: 'تابع'
    };
    return labels[type] || type;
  };

  // Get summary color
  const getSummaryColor = (status) => {
    switch (status) {
      case 'ALL_ELIGIBLE':
        return 'success';
      case 'PARTIAL':
        return 'warning';
      case 'NONE_ELIGIBLE':
        return 'error';
      default:
        return 'default';
    }
  };

  const getSummaryLabel = (status) => {
    switch (status) {
      case 'ALL_ELIGIBLE':
        return 'جميع أفراد العائلة مؤهلون';
      case 'PARTIAL':
        return 'بعض أفراد العائلة مؤهلون';
      case 'NONE_ELIGIBLE':
        return 'لا يوجد أفراد مؤهلون';
      default:
        return status;
    }
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box sx={{ p: '1.5rem' }}>
        {/* Header */}
        <Typography variant="h5" gutterBottom>
          فحص أهلية العائلة
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: '1.5rem' }}>
          تحقق من أهلية المشترك وجميع أفراد عائلته
        </Typography>

        {/* Search Form */}
        <Card sx={{ mb: '1.5rem' }}>
          <CardContent>
            <Grid container spacing={2} alignItems="flex-end">
              <Grid size={{ xs: 12, sm: 4 }}>
                <TextField
                  fullWidth
                  label="رقم المشترك (Member ID)"
                  value={memberId}
                  onChange={(e) => setMemberId(e.target.value)}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <PersonIcon />
                      </InputAdornment>
                    )
                  }}
                  onKeyPress={(e) => e.key === 'Enter' && handleCheck()}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <DatePicker
                  label="تاريخ الخدمة"
                  value={serviceDate}
                  onChange={(newValue) => setServiceDate(newValue)}
                  slotProps={{
                    textField: { fullWidth: true }
                  }}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Button
                  fullWidth
                  variant="contained"
                  size="large"
                  onClick={handleCheck}
                  disabled={loading || !memberId}
                  startIcon={loading ? <CircularProgress size={20} /> : <SearchIcon />}
                >
                  {loading ? 'جاري الفحص...' : 'فحص الأهلية'}
                </Button>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {/* Error */}
        {error && (
          <Alert severity="error" sx={{ mb: '1.5rem' }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {/* Results */}
        {result && (
          <>
            {/* Summary Card */}
            <Card sx={{ mb: '1.5rem' }}>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: '1.0rem' }}>
                  <Typography variant="h6">ملخص الأهلية</Typography>
                  <Chip label={getSummaryLabel(result.summary?.familyStatus)} color={getSummaryColor(result.summary?.familyStatus)} />
                </Box>

                <Grid container spacing={3}>
                  <Grid size={4}>
                    <Box sx={{ textAlign: 'center' }}>
                      <Typography variant="h3" color="text.secondary">
                        {result.summary?.totalMembers || 0}
                      </Typography>
                      <Typography variant="body2">إجمالي الأفراد</Typography>
                    </Box>
                  </Grid>
                  <Grid size={4}>
                    <Box sx={{ textAlign: 'center' }}>
                      <Typography variant="h3" color="success.main">
                        {result.summary?.eligibleCount || 0}
                      </Typography>
                      <Typography variant="body2">مؤهلون</Typography>
                    </Box>
                  </Grid>
                  <Grid size={4}>
                    <Box sx={{ textAlign: 'center' }}>
                      <Typography variant="h3" color="error.main">
                        {result.summary?.ineligibleCount || 0}
                      </Typography>
                      <Typography variant="body2">غير مؤهلون</Typography>
                    </Box>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>

            {/* Primary Member Card */}
            {result.primaryMember && (
              <Card sx={{ mb: '1.5rem', border: 2, borderColor: result.primaryMember.eligible ? 'success.main' : 'error.main' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: '1.0rem' }}>
                    <MemberAvatar
                      member={{
                        photoUrl: result.primaryMember.photoUrl,
                        fullName: result.primaryMember.memberNameAr || result.primaryMember.memberName
                      }}
                      size={64}
                      sx={{
                        borderColor: result.primaryMember.eligible ? 'success.main' : 'error.main',
                        borderWidth: 2
                      }}
                    />
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="h6">{result.primaryMember.memberNameAr || result.primaryMember.memberName}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {getRelationshipLabel(result.primaryMember.relationshipType)} • الرقم المدني:{' '}
                        {result.primaryMember.civilId || 'غير متوفر'} • رقم البطاقة: {result.primaryMember.cardNumber || 'غير متوفر'}
                      </Typography>
                      {result.primaryMember.employerName && (
                        <Typography variant="caption" color="text.secondary">
                          جهة العمل: {result.primaryMember.employerName}
                        </Typography>
                      )}
                    </Box>
                    {renderEligibilityChip(result.primaryMember.eligible)}
                  </Box>

                  {!result.primaryMember.eligible && result.primaryMember.reasonAr && (
                    <Alert severity="error" sx={{ mt: '1.0rem' }}>
                      {result.primaryMember.reasonAr}
                    </Alert>
                  )}

                  {result.primaryMember.policyNumber && (
                    <Box sx={{ mt: '1.0rem', p: 1, bgcolor: 'grey.100', borderRadius: 1 }}>
                      <Typography variant="caption">
                        رقم الوثيقة: {result.primaryMember.policyNumber} | فترة التغطية: {result.primaryMember.coverageStart} -{' '}
                        {result.primaryMember.coverageEnd}
                      </Typography>
                    </Box>
                  )}
                </CardContent>
              </Card>
            )}

            {/* Family Members Table */}
            {result.familyMembers?.length > 0 && (
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    أفراد العائلة ({result.familyMembers.length})
                  </Typography>

                  <TableContainer>
                    <Table>
                      <TableHead>
                        <TableRow>
                          <TableCell>الاسم</TableCell>
                          <TableCell>العلاقة</TableCell>
                          <TableCell>الرقم المدني</TableCell>
                          <TableCell>تاريخ الميلاد</TableCell>
                          <TableCell>الجنس</TableCell>
                          <TableCell>الحالة</TableCell>
                          <TableCell>الأهلية</TableCell>
                          <TableCell>السبب</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {result.familyMembers.map((member) => (
                          <TableRow key={member.memberId}>
                            <TableCell>
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                <MemberAvatar
                                  member={{
                                    photoUrl: member.photoUrl,
                                    fullName: member.memberNameAr || member.memberName
                                  }}
                                  size={32}
                                  sx={{
                                    borderColor: member.eligible ? 'success.light' : 'error.light'
                                  }}
                                />
                                <Typography variant="body2">{member.memberNameAr || member.memberName}</Typography>
                              </Box>
                            </TableCell>
                            <TableCell>{getRelationshipLabel(member.relationshipType)}</TableCell>
                            <TableCell>{member.civilId || '-'}</TableCell>
                            <TableCell>{member.dateOfBirth || '-'}</TableCell>
                            <TableCell>{member.gender === 'MALE' ? 'ذكر' : member.gender === 'FEMALE' ? 'أنثى' : '-'}</TableCell>
                            <TableCell>
                              <Chip size="small" label={member.memberStatus || 'غير محدد'} variant="outlined" />
                            </TableCell>
                            <TableCell>{renderEligibilityChip(member.eligible)}</TableCell>
                            <TableCell>
                              {member.reasonAr && (
                                <Typography variant="caption" color="error">
                                  {member.reasonAr}
                                </Typography>
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </CardContent>
              </Card>
            )}
          </>
        )}
      </Box>
    </LocalizationProvider>
  );
};

export default FamilyEligibilityPage;

