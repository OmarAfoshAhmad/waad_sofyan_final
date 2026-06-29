import { useState, useEffect, Fragment } from 'react';

// material-ui
import {
  Box,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Paper,
  CircularProgress,
  Alert,
  TextField,
  MenuItem,
  Stack,
  Button,
  FormGroup,
  FormControlLabel,
  Checkbox
} from '@mui/material';

// third-party
import * as XLSX from 'xlsx';

// project imports
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { Business, Download } from '@mui/icons-material';
import reportsService from 'services/api/reports.service';

// ==============================|| FINANCIAL CONSOLIDATION MATRIX ||============================== //

export default function FinancialConsolidationMatrix() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [data, setData] = useState([]);
  
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(currentYear);
  const [searchQuery, setSearchQuery] = useState('');
  
  const [showCompanyShare, setShowCompanyShare] = useState(true);
  const [showProviderShare, setShowProviderShare] = useState(true);

  const fetchReport = async (isBackground = false) => {
    try {
      if (!isBackground) setLoading(true);
      setError(null);
      const result = await reportsService.getFinancialConsolidation({ year: selectedYear });
      setData(result || []);
    } catch (err) {
      if (!isBackground) setError(err.message || 'حدث خطأ أثناء جلب التقرير');
    } finally {
      if (!isBackground) setLoading(false);
    }
  };

  useEffect(() => {
    fetchReport(false);
    
    // Auto-refresh polling every 30 seconds
    const intervalId = setInterval(() => {
      fetchReport(true);
    }, 30000);
    
    return () => clearInterval(intervalId);
  }, [selectedYear]);

  const handleExportExcel = () => {
    if (data.length === 0) return;

    // تحويل البيانات لشكل مناسب للإكسل
    const filteredData = data.filter(row => 
      row.employerName?.toLowerCase().includes(searchQuery.toLowerCase())
    );

    const excelData = [];
    filteredData.forEach(row => {
      let isFirstRow = true;

      // 1. حصة الشركة (Company Share)
      if (showCompanyShare) {
        excelData.push({
          'الشركة': isFirstRow ? row.employerName : '',
          'النوع': 'حصة الشركة',
          'شهر 1': row.month1?.companyDiscountAmount || 0,
          'شهر 2': row.month2?.companyDiscountAmount || 0,
          'شهر 3': row.month3?.companyDiscountAmount || 0,
          'شهر 4': row.month4?.companyDiscountAmount || 0,
          'شهر 5': row.month5?.companyDiscountAmount || 0,
          'شهر 6': row.month6?.companyDiscountAmount || 0,
          'شهر 7': row.month7?.companyDiscountAmount || 0,
          'شهر 8': row.month8?.companyDiscountAmount || 0,
          'شهر 9': row.month9?.companyDiscountAmount || 0,
          'شهر 10': row.month10?.companyDiscountAmount || 0,
          'شهر 11': row.month11?.companyDiscountAmount || 0,
          'شهر 12': row.month12?.companyDiscountAmount || 0,
          'الإجمالي الكلي': row.totalAmount?.companyDiscountAmount || 0
        });
        isFirstRow = false;
      }

      // 2. حصة المرفق (Provider Share)
      if (showProviderShare) {
        excelData.push({
          'الشركة': isFirstRow ? row.employerName : '',
          'النوع': 'حصة المرفق',
          'شهر 1': row.month1?.remainingAmount || 0,
          'شهر 2': row.month2?.remainingAmount || 0,
          'شهر 3': row.month3?.remainingAmount || 0,
          'شهر 4': row.month4?.remainingAmount || 0,
          'شهر 5': row.month5?.remainingAmount || 0,
          'شهر 6': row.month6?.remainingAmount || 0,
          'شهر 7': row.month7?.remainingAmount || 0,
          'شهر 8': row.month8?.remainingAmount || 0,
          'شهر 9': row.month9?.remainingAmount || 0,
          'شهر 10': row.month10?.remainingAmount || 0,
          'شهر 11': row.month11?.remainingAmount || 0,
          'شهر 12': row.month12?.remainingAmount || 0,
          'الإجمالي الكلي': row.totalAmount?.remainingAmount || 0
        });
        isFirstRow = false;
      }
    });

    const ws = XLSX.utils.json_to_sheet(excelData);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'الخلاصة النهائية');
    
    // Automatically triggers download in the browser without needing file-saver
    XLSX.writeFile(wb, `الخلاصة_النهائية_${selectedYear}.xlsx`);
  };

  return (
    <>
      <ModernPageHeader
        title="الخلاصة المالية المجمعة"
        subtitle="تقرير مالي شامل يوضح القيم المالية والتسويات للشركات بشكل تفصيلي"
        icon={Business}
      />

      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Stack direction="row" spacing={2} alignItems="center" mb={3} justifyContent="space-between">
            <TextField
              select
              label="السنة المالية"
              value={selectedYear}
              onChange={(e) => setSelectedYear(Number(e.target.value))}
              size="small"
              sx={{ minWidth: 150 }}
            >
              {[currentYear - 2, currentYear - 1, currentYear, currentYear + 1, currentYear + 2].map(year => (
                <MenuItem key={year} value={year}>{year}</MenuItem>
              ))}
            </TextField>

            <TextField
              label="بحث باسم الشركة..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              size="small"
              sx={{ minWidth: 200 }}
            />

            <FormGroup row sx={{ ml: 2, flexGrow: 1 }}>
              <FormControlLabel 
                control={<Checkbox checked={showCompanyShare} onChange={(e) => setShowCompanyShare(e.target.checked)} color="success" />} 
                label="عرض حصة الشركة" 
              />
              <FormControlLabel 
                control={<Checkbox checked={showProviderShare} onChange={(e) => setShowProviderShare(e.target.checked)} color="warning" />} 
                label="عرض حصة المرفق" 
              />
            </FormGroup>

            <Button 
              variant="contained" 
              color="primary" 
              startIcon={<Download />}
              onClick={handleExportExcel}
              disabled={loading || data.length === 0}
            >
              تصدير إلى Excel
            </Button>
          </Stack>

          {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

          {loading && data.length === 0 ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 5 }}>
              <CircularProgress />
            </Box>
          ) : (
            <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
              <Table size="small" sx={{ minWidth: 1000, '& .MuiTableCell-root': { borderBottom: '1px solid #eee' } }}>
                <TableHead sx={{ bgcolor: 'grey.50' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 'bold' }}>الشركة</TableCell>
                    <TableCell sx={{ fontWeight: 'bold' }}>النوع</TableCell>
                    {[...Array(12)].map((_, i) => (
                      <TableCell key={i} align="right" sx={{ fontWeight: 'bold' }}>شهر {i + 1}</TableCell>
                    ))}
                    <TableCell align="right" sx={{ fontWeight: 'bold', color: 'primary.main' }}>الإجمالي الكلي</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={15} align="center" sx={{ py: 3 }}>
                        <Typography color="textSecondary">لا توجد بيانات لهذه السنة</Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    data.filter(row => row.employerName?.toLowerCase().includes(searchQuery.toLowerCase())).length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={15} align="center" sx={{ py: 3 }}>
                          <Typography color="textSecondary">لا توجد نتائج تطابق البحث</Typography>
                        </TableCell>
                      </TableRow>
                    ) : (
                      data.filter(row => row.employerName?.toLowerCase().includes(searchQuery.toLowerCase())).map((row, index) => (
                        <Fragment key={index}>
                          {showCompanyShare && (
                            <TableRow hover sx={{ bgcolor: index % 2 === 0 ? '#ffffff' : '#fafafa' }}>
                              <TableCell component="th" scope="row" sx={{ fontWeight: 'bold', verticalAlign: 'middle', borderRight: '1px solid #eee' }} rowSpan={showCompanyShare && showProviderShare ? 2 : 1}>
                                {row.employerName}
                              </TableCell>
                              <TableCell sx={{ color: 'success.main', fontWeight: 500 }}>حصة الشركة</TableCell>
                              {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(m => (
                                <TableCell key={`tpa-${m}`} align="right" sx={{ color: 'success.main', fontWeight: 500 }}>{row[`month${m}`]?.companyDiscountAmount?.toLocaleString()}</TableCell>
                              ))}
                              <TableCell align="right" sx={{ color: 'success.main', fontWeight: 'bold' }}>{row.totalAmount?.companyDiscountAmount?.toLocaleString()}</TableCell>
                            </TableRow>
                          )}
                          
                          {showProviderShare && (
                            <TableRow hover sx={{ bgcolor: index % 2 === 0 && !showCompanyShare ? '#ffffff' : (index % 2 !== 0 && !showCompanyShare ? '#fafafa' : 'transparent') }}>
                              {!showCompanyShare && (
                                <TableCell component="th" scope="row" sx={{ fontWeight: 'bold', verticalAlign: 'middle', borderRight: '1px solid #eee' }}>
                                  {row.employerName}
                                </TableCell>
                              )}
                              <TableCell sx={{ color: 'warning.main', fontWeight: 500 }}>حصة المرفق</TableCell>
                              {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(m => (
                                <TableCell key={m} align="right" sx={{ color: 'warning.main', fontWeight: 500 }}>{row[`month${m}`]?.remainingAmount?.toLocaleString()}</TableCell>
                              ))}
                              <TableCell align="right" sx={{ fontWeight: 'bold', color: 'warning.main' }}>{row.totalAmount?.remainingAmount?.toLocaleString()}</TableCell>
                            </TableRow>
                          )}
                        </Fragment>
                      ))
                    )
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </>
  );
}
