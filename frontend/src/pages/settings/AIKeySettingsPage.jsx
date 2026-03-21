import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  IconButton,
  InputAdornment,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { EyeInvisibleOutlined, EyeOutlined, KeyOutlined } from '@ant-design/icons';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import systemSettingsService from 'services/api/systemSettings.service';

const DB_KEYS = {
  apiKey: 'AI_CLASSIFIER_API_KEY',
  model: 'AI_CLASSIFIER_MODEL',
  endpoint: 'AI_CLASSIFIER_ENDPOINT'
};

const DEFAULT_MODEL = 'qwen/qwen2.5-14b-instruct:free';
const DEFAULT_ENDPOINT = 'https://openrouter.ai/api/v1/chat/completions';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const fetchWithRetry = async (url, options, maxRetries = 2) => {
  let response;
  for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
    response = await fetch(url, options);
    if (response.status !== 429 || attempt === maxRetries) {
      return response;
    }

    const retryAfter = Number(response.headers.get('retry-after'));
    const waitMs = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * 1000 : 1000 * (2 ** attempt);
    await sleep(waitMs);
  }
  return response;
};

const AIKeySettingsPage = ({ embedded = false }) => {
  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState(DEFAULT_MODEL);
  const [endpoint, setEndpoint] = useState(DEFAULT_ENDPOINT);
  const [loading, setLoading] = useState(true);
  const [showKey, setShowKey] = useState(false);
  const [saved, setSaved] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testStatus, setTestStatus] = useState({ ok: null, message: '' });

  useEffect(() => {
    const loadFromDb = async () => {
      try {
        setLoading(true);
        const settings = await systemSettingsService.getAll();
        const map = new Map((settings || []).map((s) => [s.settingKey, s.settingValue]));
        setApiKey((map.get(DB_KEYS.apiKey) || '').trim());
        setModel((map.get(DB_KEYS.model) || DEFAULT_MODEL).trim());
        setEndpoint((map.get(DB_KEYS.endpoint) || DEFAULT_ENDPOINT).trim());
      } catch (error) {
        setTestStatus({ ok: false, message: `فشل تحميل الإعدادات من قاعدة البيانات: ${error.message || 'unknown error'}` });
      } finally {
        setLoading(false);
      }
    };
    loadFromDb();
  }, []);

  const maskedPreview = useMemo(() => {
    if (!apiKey) return 'غير محفوظ';
    if (apiKey.length <= 8) return '********';
    return `${apiKey.slice(0, 4)}********${apiKey.slice(-4)}`;
  }, [apiKey]);

  const onSave = () => {
    const saveToDb = async () => {
      try {
        await Promise.all([
          systemSettingsService.updateSetting(DB_KEYS.apiKey, apiKey.trim()),
          systemSettingsService.updateSetting(DB_KEYS.model, model.trim() || DEFAULT_MODEL),
          systemSettingsService.updateSetting(DB_KEYS.endpoint, endpoint.trim() || DEFAULT_ENDPOINT)
        ]);
        setSaved(true);
      } catch (error) {
        setSaved(false);
        setTestStatus({ ok: false, message: `فشل الحفظ في قاعدة البيانات: ${error.message || 'unknown error'}` });
      }
    };
    saveToDb();
  };

  const onClear = () => {
    const clearInDb = async () => {
      try {
        await Promise.all([
          systemSettingsService.updateSetting(DB_KEYS.apiKey, ''),
          systemSettingsService.updateSetting(DB_KEYS.model, DEFAULT_MODEL),
          systemSettingsService.updateSetting(DB_KEYS.endpoint, DEFAULT_ENDPOINT)
        ]);
        setApiKey('');
        setModel(DEFAULT_MODEL);
        setEndpoint(DEFAULT_ENDPOINT);
        setSaved(false);
        setTestStatus({ ok: null, message: '' });
      } catch (error) {
        setTestStatus({ ok: false, message: `فشل مسح الإعدادات من قاعدة البيانات: ${error.message || 'unknown error'}` });
      }
    };
    clearInDb();
  };

  const onTestConnection = async () => {
    const key = apiKey.trim();
    const modelValue = model.trim() || DEFAULT_MODEL;
    const endpointValue = endpoint.trim() || DEFAULT_ENDPOINT;

    if (!key) {
      setTestStatus({ ok: false, message: 'أدخل API Key أولاً قبل اختبار الاتصال.' });
      return;
    }

    setTesting(true);
    setTestStatus({ ok: null, message: '' });
    try {
      const response = await fetchWithRetry(
        endpointValue,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${key}`
          },
          body: JSON.stringify({
            model: modelValue,
            temperature: 0,
            max_tokens: 16,
            messages: [
              { role: 'system', content: 'You are a test assistant.' },
              { role: 'user', content: 'Reply with OK' }
            ]
          })
        },
        2
      );

      if (!response.ok) {
        const details = await response.text();
        if (response.status === 429) {
          setTestStatus({
            ok: false,
            message: 'فشل الاتصال: HTTP 429 (Rate Limit). المزود المجاني مزدحم حاليًا، جرب بعد دقيقة أو استخدم نموذج/مزود آخر.'
          });
          return;
        }
        setTestStatus({
          ok: false,
          message: `فشل الاتصال: HTTP ${response.status}${details ? ` | ${details.slice(0, 220)}` : ''}`
        });
        return;
      }

      const data = await response.json();
      const content = data?.choices?.[0]?.message?.content;
      if (!content) {
        setTestStatus({ ok: false, message: 'تم الاتصال ولكن الاستجابة من النموذج غير متوقعة.' });
        return;
      }

      setTestStatus({ ok: true, message: 'تم الاتصال بالنموذج بنجاح. يمكنك الآن استخدام صفحة تجهيز الأسعار.' });
    } catch (error) {
      setTestStatus({ ok: false, message: `تعذر الاتصال بالشبكة أو CORS: ${error.message || 'unknown error'}` });
    } finally {
      setTesting(false);
    }
  };

  return (
    <Box>
      {!embedded && (
        <ModernPageHeader
          title="إعداد API للذكاء الاصطناعي"
          subtitle="تخزين إعدادات التصنيف الذكي لاستخدامها في أدوات تجهيز الأسعار"
          icon={KeyOutlined}
        />
      )}

      <Card sx={{ mt: embedded ? 0 : 2, maxWidth: 900 }}>
        <CardContent>
          <Stack spacing={2}>
            {loading && (
              <Stack direction="row" spacing={1} alignItems="center">
                <CircularProgress size={18} />
                <Typography variant="body2">جاري تحميل الإعدادات من قاعدة البيانات...</Typography>
              </Stack>
            )}
            <Typography variant="body2" color="text.secondary">
              يتم حفظ القيم في قاعدة البيانات عبر إعدادات النظام (System Settings).
            </Typography>

            <Alert severity="warning">
              تنبيه أمني: لا تضع مفتاح API داخل الكود المصدري. استخدم هذه الصفحة للحفظ المحلي فقط.
            </Alert>

            <TextField
              label="API Key"
              fullWidth
              value={apiKey}
              onChange={(e) => {
                setApiKey(e.target.value);
                setSaved(false);
              }}
              type={showKey ? 'text' : 'password'}
              placeholder="sk-..."
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={() => setShowKey((v) => !v)} edge="end">
                      {showKey ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                    </IconButton>
                  </InputAdornment>
                )
              }}
            />

            <TextField
              label="Model"
              fullWidth
              value={model}
              onChange={(e) => {
                setModel(e.target.value);
                setSaved(false);
              }}
              placeholder="qwen/qwen2.5-14b-instruct:free"
            />

            <TextField
              label="Endpoint"
              fullWidth
              value={endpoint}
              onChange={(e) => {
                setEndpoint(e.target.value);
                setSaved(false);
              }}
              placeholder="https://openrouter.ai/api/v1/chat/completions"
            />

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <Button variant="contained" onClick={onSave}>
                حفظ الإعدادات
              </Button>
              <Button variant="outlined" onClick={onTestConnection} disabled={testing}>
                {testing ? (
                  <Stack direction="row" spacing={1} alignItems="center">
                    <CircularProgress size={16} />
                    <span>جاري الاختبار...</span>
                  </Stack>
                ) : (
                  'اختبار اتصال AI'
                )}
              </Button>
              <Button variant="outlined" color="error" onClick={onClear}>
                مسح الإعدادات
              </Button>
            </Stack>

            <Alert severity={saved ? 'success' : 'info'}>
              حالة المفتاح الحالي: {maskedPreview}
            </Alert>

            {testStatus.ok !== null && <Alert severity={testStatus.ok ? 'success' : 'error'}>{testStatus.message}</Alert>}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};

export default AIKeySettingsPage;
