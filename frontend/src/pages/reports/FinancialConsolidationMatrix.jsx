import { useState, useEffect } from 'react';

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
  Button
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

  const fetchReport = async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await reportsService.getFinancialConsolidation({ year: selectedYear });
      setData(result || []);
    } catch (err) {
      setError(err.message || 'حدث خطأ أثناء جلب التقرير');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReport();
  }, [selectedYear]);

  const handleExportExcel = () => {
    if (data.length === 0) return;

    // تحويل البيانات لشكل مناسب للإكسل
    const filteredData = data.filter(row => 
      row.employerName?.toLowerCase().includes(searchQuery.toLowerCase())
    );

    const excelData = filteredData.map(row => ({
      'الشركة': row.employerName,
      'شهر 1': row.month1,
      'شهر 2': row.month2,
      'شهر 3': row.month3,
      'شهر 4': row.month4,
      'شهر 5': row.month5,
      'شهر 6': row.month6,
      'شهر 7': row.month7,
      'شهر 8': row.month8,
      'شهر 9': row.month9,
      'شهر 10': row.month10,
      'شهر 11': row.month11,
      'شهر 12': row.month12,
      'الإجمالي الكلي': row.totalAmount
    }));

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
        subtitle="تقرير شامل يوضح القيمة المستحقة للشركة (نسب التخفيض التعاقدية) لكل جهة"
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
              sx={{ minWidth: 250, flexGrow: 1 }}
            />

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

          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 5 }}>
              <CircularProgress />
            </Box>
          ) : (
            <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
              <Table size="small" sx={{ minWidth: 1000 }}>
                <TableHead sx={{ bgcolor: 'grey.50' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 'bold' }}>الشركة</TableCell>
                    {[...Array(12)].map((_, i) => (
                      <TableCell key={i} align="right" sx={{ fontWeight: 'bold' }}>شهر {i + 1}</TableCell>
                    ))}
                    <TableCell align="right" sx={{ fontWeight: 'bold', color: 'primary.main' }}>الإجمالي الكلي</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={14} align="center" sx={{ py: 3 }}>
                        <Typography color="textSecondary">لا توجد بيانات لهذه السنة</Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    data.filter(row => row.employerName?.toLowerCase().includes(searchQuery.toLowerCase())).length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={14} align="center" sx={{ py: 3 }}>
                          <Typography color="textSecondary">لا توجد نتائج تطابق البحث</Typography>
                        </TableCell>
                      </TableRow>
                    ) : (
                      data.filter(row => row.employerName?.toLowerCase().includes(searchQuery.toLowerCase())).map((row, index) => (
                        <TableRow key={index} hover>
                        <TableCell component="th" scope="row" sx={{ fontWeight: 500 }}>
                          {row.employerName}
                        </TableCell>
                        <TableCell align="right">{row.month1?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month2?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month3?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month4?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month5?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month6?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month7?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month8?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month9?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month10?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month11?.toLocaleString()}</TableCell>
                        <TableCell align="right">{row.month12?.toLocaleString()}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
                          {row.totalAmount?.toLocaleString()}
                        </TableCell>
                        </TableRow>
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
