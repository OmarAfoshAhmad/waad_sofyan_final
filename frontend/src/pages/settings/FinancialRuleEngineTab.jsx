import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Rule as RuleIcon,
  Save as SaveIcon
} from '@mui/icons-material';
import coverageRuleEngineService from 'services/api/coverageRuleEngine.service';

const RULE_TYPES = [
  'TIMES_LIMIT_RULE',
  'COVERAGE_PERCENT_RULE',
  'AMOUNT_LIMIT_RULE',
  'COPAY_RULE',
  'DEDUCTIBLE_RULE'
];

const RULE_GROUPS = [
  'PRE_VALIDATION_RULES',
  'COVERAGE_CALCULATION_RULES',
  'LIMIT_ENFORCEMENT_RULES',
  'POST_PROCESSING_RULES'
];

const EMPTY_RULE = {
  id: null,
  name: '',
  type: 'TIMES_LIMIT_RULE',
  priority: 10,
  enabled: true,
  ruleGroup: 'PRE_VALIDATION_RULES',
  dependencyRules: [],
  configurationText: '{}',
  isNew: true
};

const normalizeRule = (r) => ({
  id: r.id,
  name: r.name || '',
  type: r.type || 'TIMES_LIMIT_RULE',
  priority: Number.isFinite(Number(r.priority)) ? Number(r.priority) : 10,
  enabled: Boolean(r.enabled),
  ruleGroup: r.ruleGroup || 'PRE_VALIDATION_RULES',
  dependencyRules: Array.isArray(r.dependencyRules) ? r.dependencyRules : [],
  configurationText: JSON.stringify(r.configuration || {}, null, 2),
  isNew: false
});

const FinancialRuleEngineTab = () => {
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [rules, setRules] = useState([]);

  const loadRules = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      const rows = await coverageRuleEngineService.getAll();
      setRules((rows || []).map(normalizeRule));
    } catch (e) {
      setError(e?.response?.data?.message || 'فشل تحميل قواعد التغطية');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRules();
  }, [loadRules]);

  const ruleNameOptions = useMemo(() => {
    return rules.map((r) => r.name).filter(Boolean);
  }, [rules]);

  const updateRule = (index, patch) => {
    setRules((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };

  const addRule = () => {
    setRules((prev) => [
      ...prev,
      {
        ...EMPTY_RULE,
        priority: prev.length > 0 ? Math.max(...prev.map((r) => Number(r.priority) || 0)) + 10 : 10
      }
    ]);
  };

  const removeRule = async (index) => {
    const row = rules[index];
    if (!row) return;

    if (row.id == null) {
      setRules((prev) => prev.filter((_, i) => i !== index));
      return;
    }

    try {
      setIsSaving(true);
      setError(null);
      await coverageRuleEngineService.remove(row.id);
      setRules((prev) => prev.filter((_, i) => i !== index));
      setSuccess('تم حذف القاعدة');
      setTimeout(() => setSuccess(null), 2000);
    } catch (e) {
      setError(e?.response?.data?.message || 'فشل حذف القاعدة');
    } finally {
      setIsSaving(false);
    }
  };

  const saveRule = async (index) => {
    const row = rules[index];
    if (!row) return;

    if (!row.name.trim()) {
      setError('اسم القاعدة مطلوب');
      return;
    }

    let parsedConfig;
    try {
      parsedConfig = row.configurationText?.trim() ? JSON.parse(row.configurationText) : {};
    } catch (_e) {
      setError(`تنسيق JSON غير صحيح في إعدادات القاعدة: ${row.name || 'قاعدة جديدة'}`);
      return;
    }

    try {
      setIsSaving(true);
      setError(null);

      const payload = {
        name: row.name.trim(),
        type: row.type,
        priority: Number(row.priority),
        enabled: Boolean(row.enabled),
        ruleGroup: row.ruleGroup,
        dependencyRules: row.dependencyRules || [],
        configuration: parsedConfig || {}
      };

      const saved = row.id == null
        ? await coverageRuleEngineService.create(payload)
        : await coverageRuleEngineService.update(row.id, payload);

      updateRule(index, normalizeRule(saved));
      setSuccess('تم حفظ القاعدة بنجاح');
      setTimeout(() => setSuccess(null), 2000);
    } catch (e) {
      setError(e?.response?.data?.message || 'فشل حفظ القاعدة');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight={220}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ p: '1.0rem', pb: 0 }}>
        {error && (
          <Alert severity="error" sx={{ mb: '0.75rem' }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        {success && (
          <Alert severity="info" sx={{ mb: '0.75rem' }} onClose={() => setSuccess(null)}>
            {success}
          </Alert>
        )}

        <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem' }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={1}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <RuleIcon color="primary" sx={{ fontSize: '1.25rem' }} />
              <Typography variant="subtitle2" fontWeight={700} color="primary.main">
                قواعد محرك التغطية المالية
              </Typography>
            </Stack>
            <Button variant="outlined" startIcon={<AddIcon />} onClick={addRule} disabled={isSaving}>
              إضافة قاعدة
            </Button>
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            يمكنك تعديل التفعيل، الترتيب، المجموعة، الاعتمادات، وإعدادات JSON لكل قاعدة بدون أي تغيير في الكود.
          </Typography>
        </Paper>
      </Box>

      <Box sx={{ flex: 1, overflow: 'auto', p: '1.0rem' }}>
        <Stack spacing={2}>
          {rules.map((rule, index) => (
            <Paper key={rule.id ?? `new-${index}`} variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem' }}>
              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField
                    fullWidth
                    size="small"
                    label="اسم القاعدة"
                    value={rule.name}
                    onChange={(e) => updateRule(index, { name: e.target.value })}
                  />
                </Grid>

                <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                  <TextField
                    select
                    fullWidth
                    size="small"
                    label="نوع القاعدة"
                    value={rule.type}
                    onChange={(e) => updateRule(index, { type: e.target.value })}
                  >
                    {RULE_TYPES.map((type) => (
                      <MenuItem key={type} value={type}>{type}</MenuItem>
                    ))}
                  </TextField>
                </Grid>

                <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                  <TextField
                    fullWidth
                    type="number"
                    size="small"
                    label="الأولوية"
                    value={rule.priority}
                    onChange={(e) => updateRule(index, { priority: Number(e.target.value) })}
                  />
                </Grid>

                <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                  <TextField
                    select
                    fullWidth
                    size="small"
                    label="المجموعة"
                    value={rule.ruleGroup}
                    onChange={(e) => updateRule(index, { ruleGroup: e.target.value })}
                  >
                    {RULE_GROUPS.map((group) => (
                      <MenuItem key={group} value={group}>{group}</MenuItem>
                    ))}
                  </TextField>
                </Grid>

                <Grid size={{ xs: 12, sm: 6, md: 2 }}>
                  <FormControlLabel
                    control={<Switch checked={rule.enabled} onChange={(e) => updateRule(index, { enabled: e.target.checked })} />}
                    label="مفعلة"
                    sx={{ m: 0, mt: '0.3rem' }}
                  />
                </Grid>

                <Grid size={12}>
                  <TextField
                    select
                    fullWidth
                    size="small"
                    label="اعتماد على قواعد"
                    SelectProps={{ multiple: true, renderValue: (selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {(selected || []).map((value) => <Chip key={value} size="small" label={value} />)}
                      </Box>
                    ) }}
                    value={rule.dependencyRules || []}
                    onChange={(e) => updateRule(index, { dependencyRules: e.target.value })}
                  >
                    {ruleNameOptions
                      .filter((name) => name !== rule.name)
                      .map((name) => (
                        <MenuItem key={name} value={name}>{name}</MenuItem>
                      ))}
                  </TextField>
                </Grid>

                <Grid size={12}>
                  <TextField
                    fullWidth
                    multiline
                    minRows={4}
                    size="small"
                    label="Configuration JSON"
                    value={rule.configurationText}
                    onChange={(e) => updateRule(index, { configurationText: e.target.value })}
                  />
                </Grid>

                <Grid size={12}>
                  <Divider sx={{ my: '0.3rem' }} />
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <IconButton color="error" onClick={() => removeRule(index)} disabled={isSaving}>
                      <DeleteIcon />
                    </IconButton>
                    <Button
                      variant="contained"
                      onClick={() => saveRule(index)}
                      startIcon={isSaving ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />}
                      disabled={isSaving}
                    >
                      حفظ القاعدة
                    </Button>
                  </Stack>
                </Grid>
              </Grid>
            </Paper>
          ))}

          {rules.length === 0 && (
            <Paper variant="outlined" sx={{ p: '1.0rem', borderRadius: '0.25rem' }}>
              <Typography variant="body2" color="text.secondary">
                لا توجد قواعد حالياً. اضغط "إضافة قاعدة" للبدء.
              </Typography>
            </Paper>
          )}
        </Stack>
      </Box>
    </Box>
  );
};

export default FinancialRuleEngineTab;
