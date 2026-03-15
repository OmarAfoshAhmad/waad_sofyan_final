/**
 * ============================================================================
 * Company Settings Page - System Branding & Identity
 * ============================================================================
 *
 * Page for managing TBA company information, branding, and identity.
 *
 * Features:
 * - Edit company name, code, and status
 * - Branding: logo, business type
 * - Contact info: phone, email, address, website
 * - Tax/registration details
 * - Form validation
 * - Auto-save with success/error notifications
 * - RTL support
 *
 * Architecture:
 * - Single company context (no multi-tenant)
 * - Uses useSystemCompany() hook (no hardcoded codes)
 * - All branding fields optional (if empty, not displayed)
 *
 * Permissions Required:
 * - SUPER_ADMIN or MANAGE_COMPANIES
 *
 * @created 2026-01-02
 * @updated 2026-01-02 - Added branding fields
 */

import { useState, useEffect, useRef } from 'react';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  FormControlLabel,
  Radio,
  RadioGroup,
  FormControl,
  FormLabel,
  Grid,
  Typography,
  Divider,
  CircularProgress,
  Alert,
  Chip
} from '@mui/material';
import { Save as SaveIcon, CloudUpload as CloudUploadIcon, RestartAlt as ResetIcon } from '@mui/icons-material';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import { useSystemCompany, useUpdateCompany } from 'hooks/useCompany';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

const CompanySettingsPage = () => {
  // ============================================================================
  // STATE MANAGEMENT
  // ============================================================================

  const [formData, setFormData] = useState({
    id: null,
    name: '',
    code: '',
    active: true,
    // Branding fields
    logoUrl: '',
    businessType: '',
    phone: '',
    email: '',
    address: '',
    website: '',
    taxNumber: ''
  });

  const [errors, setErrors] = useState({});

  // ============================================================================
  // LOGO MANAGEMENT (localStorage-based via CompanySettingsContext)
  // ============================================================================
  const { getLogoSrc, updateSettings, resetToDefaultLogo, hasCustomLogo } = useCompanySettings();
  const fileInputRef = useRef(null);

  const handleLogoFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (evt) => {
      updateSettings({ logoBase64: evt.target.result, logoUrl: null });
    };
    reader.readAsDataURL(file);
  };

  const handleResetDefaultLogo = () => {
    resetToDefaultLogo();
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  // ============================================================================
  // DATA FETCHING
  // ============================================================================

  // Fetch system company (single-company mode)
  const { data: company, isLoading, error } = useSystemCompany();
  const updateCompanyMutation = useUpdateCompany();

  // Populate form when data is loaded
  useEffect(() => {
    if (company?.data) {
      // Response structure: ApiResponse<CompanyDto>
      // axios returns: response.data = { success, message, data: CompanyDto }
      // service returns: response.data
      // hook gets: { success, message, data: CompanyDto }
      const companyData = company.data; // This is the CompanyDto
      console.log('🏢 Company data received:', companyData);
      console.log('🆔 Company ID:', companyData?.id);

      if (!companyData) {
        console.error('❌ Company data is null or undefined');
        return;
      }

      setFormData({
        id: companyData.id,
        name: companyData.name || '',
        code: companyData.code || '',
        active: companyData.active !== undefined ? companyData.active : true,
        // Branding fields (allow empty)
        logoUrl: companyData.logoUrl || '',
        businessType: companyData.businessType || '',
        phone: companyData.phone || '',
        email: companyData.email || '',
        address: companyData.address || '',
        website: companyData.website || '',
        taxNumber: companyData.taxNumber || ''
      });
    }
  }, [company]);

  // ============================================================================
  // FORM HANDLERS
  // ============================================================================

  const handleChange = (field) => (event) => {
    const value = field === 'active' ? event.target.value === 'true' : event.target.value;
    setFormData((prev) => ({
      ...prev,
      [field]: value
    }));

    // Clear error for this field
    if (errors[field]) {
      setErrors((prev) => ({
        ...prev,
        [field]: null
      }));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.name || formData.name.trim() === '') {
      newErrors.name = 'اسم الشركة مطلوب';
    }

    if (!formData.code || formData.code.trim() === '') {
      newErrors.code = 'كود الشركة مطلوب';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    const { id, ...updateData } = formData;

    console.log('📝 Submitting company update:');
    console.log('   ID:', id);
    console.log('   Data:', updateData);

    updateCompanyMutation.mutate({
      id: id,
      data: updateData
    });
  };

  // ============================================================================
  // RENDER LOADING STATE
  // ============================================================================

  if (isLoading) {
    return (
      <Box>
        <ModernPageHeader title="إعدادات الشركة" subtitle="إدارة معلومات شركة TBA للمراجعة الطبية" />
        <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
          <CircularProgress />
        </Box>
      </Box>
    );
  }

  // ============================================================================
  // RENDER ERROR STATE
  // ============================================================================

  if (error) {
    return (
      <Box>
        <ModernPageHeader title="إعدادات الشركة" subtitle="إدارة معلومات شركة TBA للمراجعة الطبية" />
        <Alert severity="error" sx={{ mt: '1.5rem' }}>
          حدث خطأ أثناء تحميل بيانات الشركة. يرجى المحاولة مرة أخرى.
        </Alert>
      </Box>
    );
  }

  // ============================================================================
  // RENDER MAIN FORM
  // ============================================================================

  return (
    <Box>
      {/* Page Header */}
      <ModernPageHeader title="إعدادات الشركة" subtitle="إدارة معلومات شركة TBA للمراجعة الطبية" />

      {/* Company Settings Form */}
      <Box component="form" onSubmit={handleSubmit} sx={{ mt: '1.5rem' }}>
        <Card>
          <CardContent>
            <Box sx={{ mb: '1.5rem' }}>
              <Typography variant="h6" gutterBottom>
                معلومات الشركة الأساسية
              </Typography>
              <Divider />
            </Box>

            <Grid container spacing={3}>
              {/* Company Name */}
              <Grid size={12}>
                <TextField
                  fullWidth
                  label="اسم الشركة"
                  name="name"
                  value={formData.name}
                  onChange={handleChange('name')}
                  error={!!errors.name}
                  helperText={errors.name}
                  required
                  placeholder="شركة TBA للمراجعة الطبية"
                />
              </Grid>

              {/* Company Code */}
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="كود الشركة"
                  name="code"
                  value={formData.code}
                  onChange={handleChange('code')}
                  error={!!errors.code}
                  helperText={errors.code}
                  required
                  placeholder="TBA"
                />
              </Grid>

              {/* Business Type */}
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="نوع النشاط"
                  name="businessType"
                  value={formData.businessType}
                  onChange={handleChange('businessType')}
                  placeholder="مراجعة طبية"
                />
              </Grid>

              {/* Phone */}
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="الهاتف"
                  name="phone"
                  value={formData.phone}
                  onChange={handleChange('phone')}
                  placeholder="+966 XX XXX XXXX"
                />
              </Grid>

              {/* Email */}
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="البريد الإلكتروني"
                  name="email"
                  type="email"
                  value={formData.email}
                  onChange={handleChange('email')}
                  placeholder="info@tba.com.sa"
                />
              </Grid>

              {/* Website */}
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="الموقع الإلكتروني"
                  name="website"
                  value={formData.website}
                  onChange={handleChange('website')}
                  placeholder="https://www.tba.com.sa"
                />
              </Grid>

              {/* Tax Number */}
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="الرقم الضريبي"
                  name="taxNumber"
                  value={formData.taxNumber}
                  onChange={handleChange('taxNumber')}
                  placeholder="300000000000003"
                />
              </Grid>

              {/* Address */}
              <Grid size={12}>
                <TextField
                  fullWidth
                  label="العنوان"
                  name="address"
                  multiline
                  rows={3}
                  value={formData.address}
                  onChange={handleChange('address')}
                  placeholder="المملكة العربية السعودية، الرياض"
                />
              </Grid>

              {/* Logo URL - hidden: logo is managed via the Logo section below */}

              {/* Active Status */}
              <Grid size={{ xs: 12, md: 6 }}>
                <FormControl component="fieldset">
                  <FormLabel component="legend">الحالة</FormLabel>
                  <RadioGroup row name="active" value={formData.active.toString()} onChange={handleChange('active')}>
                    <FormControlLabel value="true" control={<Radio />} label="نشط" />
                    <FormControlLabel value="false" control={<Radio />} label="غير نشط" />
                  </RadioGroup>
                </FormControl>
              </Grid>

              {/* Submit Button */}
              <Grid size={12}>
                <Box display="flex" justifyContent="center" sx={{ mt: '1.0rem' }}>
                  <Button
                    type="submit"
                    variant="contained"
                    color="primary"
                    size="large"
                    startIcon={updateCompanyMutation.isPending ? <CircularProgress size={20} /> : <SaveIcon />}
                    disabled={updateCompanyMutation.isPending}
                    sx={{ minWidth: '12.5rem' }}
                  >
                    {updateCompanyMutation.isPending ? 'جاري الحفظ...' : 'حفظ'}
                  </Button>
                </Box>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      </Box>

      {/* ================================================================ */}
      {/* LOGO MANAGEMENT CARD                                             */}
      {/* ================================================================ */}
      <Card sx={{ mt: '1.5rem' }}>
        <CardContent>
          <Box sx={{ mb: '1.0rem' }}>
            <Typography variant="h6" gutterBottom>شعار الشركة</Typography>
            <Divider />
          </Box>

          {/* Preview */}
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem', mb: '1.5rem' }}>
            <Box
              component="img"
              src={getLogoSrc()}
              alt="شعار الشركة"
              sx={{
                maxHeight: '8.125rem',
                maxWidth: '18.75rem',
                objectFit: 'contain',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: '0.25rem',
                p: '1.0rem',
                bgcolor: '#fafafa'
              }}
            />
            <Chip
              label={hasCustomLogo ? 'شعار مخصص' : 'الشعار الافتراضي'}
              color={hasCustomLogo ? 'primary' : 'default'}
              size="small"
            />
          </Box>

          {/* Buttons */}
          <Box sx={{ display: 'flex', gap: '1.0rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png, image/jpeg, image/svg+xml, image/webp"
              style={{ display: 'none' }}
              onChange={handleLogoFileChange}
            />
            <Button
              variant="contained"
              startIcon={<CloudUploadIcon />}
              onClick={() => fileInputRef.current?.click()}
            >
              رفع شعار مخصص
            </Button>
            {hasCustomLogo && (
              <Button
                variant="outlined"
                color="warning"
                startIcon={<ResetIcon />}
                onClick={handleResetDefaultLogo}
              >
                استعادة الشعار الافتراضي
              </Button>
            )}
          </Box>

          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', mt: '1.0rem' }}>
            يظهر الشعار في جميع التقارير والطباعة — يُخزَّن محلياً في المتصفح
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
};

export default CompanySettingsPage;


