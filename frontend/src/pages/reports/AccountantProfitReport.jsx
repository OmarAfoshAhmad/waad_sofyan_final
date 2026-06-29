import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Grid,
  TextField,
  MenuItem,
  Button,
  Typography,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  Paper,
  Alert,
  CircularProgress
} from '@mui/material';
import { Download as DownloadIcon, Refresh as RefreshIcon } from '@mui/icons-material';
import { ModernPageHeader } from 'components/tba';
import { reportsService } from 'services/api';
import employersService from 'services/api/employers.service';
import { formatCurrency } from 'utils/currency-formatter';
import { exportToExcel } from 'utils/exportUtils';

const AccountantProfitReport = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [reportData, setReportData] = useState([]);
  
  const [employers, setEmployers] = useState([]);
  const [selectedEmployer, setSelectedEmployer] = useState('');
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());
  const [selectedMonth, setSelectedMonth] = useState('');

  // Fetch employers for the dropdown
  useEffect(() => {
    const fetchEmployers = async () => {
      try {
        const res = await employersService.getEmployers();
        setEmployers(res || []);
      } catch (err) {
        console.error('Failed to fetch employers', err);
      }
    };
    fetchEmployers();
  }, []);

  const fetchReport = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await reportsService.getCompanyProfitReport({
        employerId: selectedEmployer || null,
        year: selectedYear,
        month: selectedMonth || null
      });
      setReportData(data || []);
    } catch (err) {
      setError(err.message || 'حدث خطأ أثناء جلب التقرير');
    } finally {
      setLoading(false);
    }
  };

  // Auto-fetch on dependency changes
  useEffect(() => {
    if (selectedYear) {
      fetchReport();
    }
  }, [selectedEmployer, selectedYear, selectedMonth]);

  const handleExportExcel = () => {
    if (!reportData || reportData.length === 0) return;
    
    const excelData = reportData.map(row => {
      const rowData = {};
      if (!selectedEmployer) rowData['اسم الشركة'] = row.employerName;
      rowData['اسم المرفق الصحي'] = row.providerName;
      rowData['الشهر'] = String(row.month);
      rowData['قيمة المطالبة'] = row.totalClaimValue;
      rowData['نسبة التخفيض'] = `${row.discountPercent}%`;
      rowData['القيمة المستحقة للشركة'] = row.companyDueValue;
      return rowData;
    });

    exportToExcel(excelData, `تقرير_المحاسب_${selectedYear}`);
  };

  // Calculate totals
  const totalClaims = reportData.reduce((sum, row) => sum + (row.totalClaimValue || 0), 0);
  const totalDue = reportData.reduce((sum, row) => sum + (row.companyDueValue || 0), 0);

  return (
    <Box>
      <ModernPageHeader
        title="تقرير أرباح الخصومات (تقرير المحاسب)"
        subtitle="يعرض هذا التقرير القيمة المستحقة للشركة من التخفيضات المتفق عليها مع مقدمي الخدمة."
      />

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2} alignItems="stretch">
            {/* الصف الأول: الفلاتر */}
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                select
                fullWidth
                label="الشركة"
                value={selectedEmployer}
                onChange={(e) => setSelectedEmployer(e.target.value)}
              >
                <MenuItem value="">كل الشركات</MenuItem>
                {employers.map((emp) => (
                  <MenuItem key={emp.id} value={emp.id}>
                    {emp.name}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                type="number"
                fullWidth
                label="السنة"
                value={selectedYear}
                onChange={(e) => setSelectedYear(e.target.value)}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <TextField
                select
                fullWidth
                label="الشهر"
                value={selectedMonth}
                onChange={(e) => setSelectedMonth(e.target.value)}
              >
                <MenuItem value="">كل الأشهر</MenuItem>
                {[...Array(12)].map((_, i) => (
                  <MenuItem key={i + 1} value={i + 1}>
                    {i + 1}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            {/* الأزرار في مساحة أوسع */}
            <Grid size={{ xs: 12, md: 2 }}>
              <Button
                fullWidth
                variant="contained"
                onClick={fetchReport}
                disabled={loading}
                startIcon={<RefreshIcon />}
                sx={{ height: '100%' }}
              >
                تحديث
              </Button>
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <Button
                fullWidth
                variant="outlined"
                onClick={handleExportExcel}
                disabled={reportData.length === 0}
                startIcon={<DownloadIcon />}
                color="success"
                sx={{ height: '100%' }}
              >
                تصدير Excel
              </Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper} elevation={2}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead sx={{ bgcolor: 'primary.light' }}>
            <TableRow>
              {!selectedEmployer && <TableCell sx={{ fontWeight: 'bold' }}>اسم الشركة</TableCell>}
              <TableCell sx={{ fontWeight: 'bold' }}>اسم المرفق الصحي</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>شهر</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }} align="right">قيمة المطالبة</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }} align="right">نسبة التخفيض</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }} align="right">قيمة المستحقة للشركة</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={!selectedEmployer ? 6 : 5} align="center" sx={{ py: 3 }}>
                  <CircularProgress />
                </TableCell>
              </TableRow>
            ) : reportData.length === 0 ? (
              <TableRow>
                <TableCell colSpan={!selectedEmployer ? 6 : 5} align="center" sx={{ py: 3 }}>
                  <Typography variant="body1" color="textSecondary">لا توجد بيانات لعرضها.</Typography>
                </TableCell>
              </TableRow>
            ) : (
              <>
                {reportData.map((row, index) => (
                  <TableRow key={index} hover>
                    {!selectedEmployer && <TableCell>{row.employerName}</TableCell>}
                    <TableCell>{row.providerName}</TableCell>
                    <TableCell>{row.month}</TableCell>
                    <TableCell align="right">{formatCurrency(row.totalClaimValue)}</TableCell>
                    <TableCell align="right">{row.discountPercent}%</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 'bold', color: 'success.main' }}>
                      {formatCurrency(row.companyDueValue)}
                    </TableCell>
                  </TableRow>
                ))}
                {/* الإجماليات */}
                <TableRow sx={{ bgcolor: 'grey.100' }}>
                  <TableCell colSpan={!selectedEmployer ? 3 : 2} sx={{ fontWeight: 'bold' }}>الإجمالي</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                    {formatCurrency(totalClaims)}
                  </TableCell>
                  <TableCell align="right"></TableCell>
                  <TableCell align="right" sx={{ fontWeight: 'bold', color: 'success.main', fontSize: '1.1rem' }}>
                    {formatCurrency(totalDue)}
                  </TableCell>
                </TableRow>
              </>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default AccountantProfitReport;
