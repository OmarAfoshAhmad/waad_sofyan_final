import { useState } from 'react';

// material-ui
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import Avatar from '@mui/material/Avatar';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';

// project imports
import MainCard from 'components/MainCard';
import { openSnackbar } from 'api/snackbar';

// icons
import { SaveOutlined, UploadOutlined, BankOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';

// ==============================|| SYSTEM SETTINGS - COMPANY INFO ||============================== //

export default function TabCompanyInfo() {
  const initialValues = {
    companyNameEn: 'TBA WAAD Insurance Administration',
    companyNameAr: 'إدارة التأمينات الصحية TBA WAAD',
    registrationNumber: 'LY-REG-2024-001',
    taxId: 'TAX-LY-123456789',
    address: 'Tripoli, Libya\nMedical Insurance Building\n3rd Floor',
    phone: '+218 21 123 4567',
    email: 'info@tba-waad.ly',
    website: 'https://www.tba-waad.ly',
    brandPrimaryColor: '#1976d2',
    brandSecondaryColor: '#dc004e'
  };

  const [formData, setFormData] = useState(initialValues);
  const [logoPreview, setLogoPreview] = useState(null);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  const handleChange = (field) => (event) => {
    setFormData({ ...formData, [field]: event.target.value });
    // Clear error for this field
    if (errors[field]) {
      setErrors({ ...errors, [field]: '' });
    }
  };

  const validateForm = () => {
    const newErrors = {};

    // Email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (formData.email && !emailRegex.test(formData.email)) {
      newErrors.email = 'Invalid email format';
    }

    // Website validation
    const urlRegex = /^https?:\/\/.+\..+/;
    if (formData.website && !urlRegex.test(formData.website)) {
      newErrors.website = 'Invalid website URL (must start with http:// or https://)';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleReset = () => {
    setFormData(initialValues);
    setLogoPreview(null);
    setErrors({});
  };

  const handleLogoUpload = (event) => {
    const file = event.target.files[0];
    if (file) {
      // Validate file type
      const validTypes = ['image/jpeg', 'image/png', 'image/jpg'];
      if (!validTypes.includes(file.type)) {
        openSnackbar({
          open: true,
          message: 'Invalid file type. Please upload JPG or PNG image',
          variant: 'warning'
        });
        return;
      }

      // Validate file size (max 2MB)
      if (file.size > 2 * 1024 * 1024) {
        openSnackbar({
          open: true,
          message: 'File size too large. Maximum size is 2MB',
          variant: 'warning'
        });
        return;
      }

      const reader = new FileReader();
      reader.onloadend = () => {
        setLogoPreview(reader.result);
      };
      reader.readAsDataURL(file);
    }
  };

  const handleRemoveLogo = () => {
    setLogoPreview(null);
  };

  const handleSave = async () => {
    // Validate form first
    if (!validateForm()) {
      openSnackbar({
        open: true,
        message: 'Please fix validation errors before saving',
        variant: 'warning'
      });
      return;
    }

    setLoading(true);
    try {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // Save to localStorage for demo
      const dataToSave = { ...formData };
      if (logoPreview) {
        dataToSave.logo = logoPreview;
      }
      localStorage.setItem('system_company_info', JSON.stringify(dataToSave));

      openSnackbar({
        open: true,
        message: 'Company information saved successfully',
        variant: 'success'
      });
    } catch {
      openSnackbar({
        open: true,
        message: 'Failed to save company information',
        variant: 'error'
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Grid container spacing={3}>
      <Grid size={12}>
        <MainCard title="Company Information">
          <Grid container spacing={3}>
            {/* Company Logo */}
            <Grid size={12}>
              <Divider textAlign="left">Company Logo</Divider>
            </Grid>

            <Grid size={12}>
              <Stack direction="row" spacing={3} alignItems="center">
                <Avatar variant="rounded" sx={{ width: '7.5rem', height: '7.5rem', bgcolor: 'primary.lighter' }} src={logoPreview}>
                  <BankOutlined style={{ fontSize: '3.0rem' }} />
                </Avatar>
                <Box>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <input
                      accept="image/png,image/jpeg,image/jpg"
                      style={{ display: 'none' }}
                      id="logo-upload"
                      type="file"
                      onChange={handleLogoUpload}
                    />
                    <label htmlFor="logo-upload">
                      <Button variant="outlined" component="span" startIcon={<UploadOutlined />}>
                        Upload Logo
                      </Button>
                    </label>
                    {logoPreview && (
                      <IconButton color="error" onClick={handleRemoveLogo} size="small">
                        <DeleteOutlined />
                      </IconButton>
                    )}
                  </Stack>
                  <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                    Recommended: 400x400px, PNG or JPG (Max 2MB)
                  </Typography>
                </Box>
              </Stack>
            </Grid>

            {/* Company Names */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Company Names
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Company Name (English)"
                value={formData.companyNameEn}
                onChange={handleChange('companyNameEn')}
                placeholder="Enter company name in English"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Company Name (Arabic)"
                value={formData.companyNameAr}
                onChange={handleChange('companyNameAr')}
                placeholder="أدخل اسم الشركة بالعربية"
                dir="rtl"
              />
            </Grid>

            {/* Legal Information */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Legal Information
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Registration Number"
                value={formData.registrationNumber}
                onChange={handleChange('registrationNumber')}
                placeholder="Enter registration number"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Tax ID"
                value={formData.taxId}
                onChange={handleChange('taxId')}
                placeholder="Enter tax identification number"
              />
            </Grid>

            {/* Contact Information */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Contact Information
              </Divider>
            </Grid>

            <Grid size={12}>
              <TextField
                fullWidth
                label="Address"
                value={formData.address}
                onChange={handleChange('address')}
                placeholder="Street address\nCity, Country\nPostal code"
                multiline
                rows={3}
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Phone"
                value={formData.phone}
                onChange={handleChange('phone')}
                placeholder="+218 XX XXX XXXX"
                helperText="Format: +218 XX XXX XXXX"
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Email"
                type="email"
                value={formData.email}
                onChange={handleChange('email')}
                placeholder="info@example.com"
                error={!!errors.email}
                helperText={errors.email || 'Company support email address'}
              />
            </Grid>

            <Grid size={12}>
              <TextField
                fullWidth
                label="Website"
                value={formData.website}
                onChange={handleChange('website')}
                placeholder="https://www.example.com"
                error={!!errors.website}
                helperText={errors.website || 'Company website URL (e.g., https://example.com)'}
              />
            </Grid>

            {/* Branding */}
            <Grid size={12}>
              <Divider textAlign="left" sx={{ mt: '1.0rem' }}>
                Brand Colors
              </Divider>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction="row" spacing={2} alignItems="center">
                <TextField
                  fullWidth
                  label="Primary Brand Color"
                  value={formData.brandPrimaryColor}
                  onChange={handleChange('brandPrimaryColor')}
                  placeholder="#1976d2"
                />
                <Box
                  sx={{
                    width: '3.75rem',
                    height: '2.5rem',
                    bgcolor: formData.brandPrimaryColor,
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1
                  }}
                />
              </Stack>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction="row" spacing={2} alignItems="center">
                <TextField
                  fullWidth
                  label="Secondary Brand Color"
                  value={formData.brandSecondaryColor}
                  onChange={handleChange('brandSecondaryColor')}
                  placeholder="#dc004e"
                />
                <Box
                  sx={{
                    width: '3.75rem',
                    height: '2.5rem',
                    bgcolor: formData.brandSecondaryColor,
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1
                  }}
                />
              </Stack>
            </Grid>

            {/* Save Button */}
            <Grid size={12}>
              <Stack direction="row" spacing={2} justifyContent="flex-end" sx={{ mt: '1.0rem' }}>
                <Button variant="outlined" startIcon={<ReloadOutlined />} onClick={handleReset} disabled={loading}>
                  Reset
                </Button>
                <Button variant="contained" startIcon={<SaveOutlined />} onClick={handleSave} disabled={loading}>
                  {loading ? 'Saving...' : 'Save Changes'}
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </MainCard>
      </Grid>
    </Grid>
  );
}
