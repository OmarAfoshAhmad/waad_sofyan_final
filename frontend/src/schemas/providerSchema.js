import * as yup from 'yup';

/**
 * Provider Form Validation Schema
 *
 * Shared between ProviderCreate and ProviderEdit
 * Enforces:
 * - Required fields
 * - Email format
 * - Phone format
 * - Contract date logic (endDate > startDate)
 * - Discount rate range (0-100)
 * - Password strength (for account creation)
 */

// Email validation regex
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Password strength validation
const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

/**
 * Provider basic information schema
 */
export const providerFormSchema = yup.object({
  name: yup
    .string()
    .required('اسم مقدم الخدمة مطلوب')
    .min(3, 'الاسم يجب أن يكون 3 أحرف على الأقل')
    .max(200, 'الاسم يجب ألا يتجاوز 200 حرف'),

  licenseNumber: yup.string().required('رقم الترخيص مطلوب').min(3, 'رقم الترخيص يجب أن يكون 3 أحرف على الأقل'),

  taxNumber: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value)),

  providerType: yup.string().required('نوع المزود مطلوب').oneOf(['HOSPITAL', 'CLINIC', 'LAB', 'PHARMACY', 'OTHER'], 'نوع المزود غير صالح'),

  networkStatus: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .oneOf(['IN_NETWORK', 'OUT_NETWORK', 'CONTRACTED', null], 'حالة الشبكة غير صالحة'),

  city: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .max(100, 'اسم المدينة يجب ألا يتجاوز 100 حرف'),

  address: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .max(500, 'العنوان يجب ألا يتجاوز 500 حرف'),

  phone: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .matches(/^[+\d\s-()]*$/, 'رقم الهاتف غير صحيح'),

  email: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .matches(EMAIL_REGEX, 'البريد الإلكتروني غير صحيح'),

  // Contract validation with date logic
  contractStartDate: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .test('valid-date', 'تاريخ بداية العقد غير صالح', (value) => {
      if (!value) return true; // Optional field
      const date = new Date(value);
      return !isNaN(date.getTime());
    }),

  contractEndDate: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .test('valid-date', 'تاريخ نهاية العقد غير صالح', (value) => {
      if (!value) return true; // Optional field
      const date = new Date(value);
      return !isNaN(date.getTime());
    })
    .test('end-after-start', 'تاريخ انتهاء العقد يجب أن يكون بعد تاريخ البداية', function (endDate) {
      const { contractStartDate } = this.parent;
      if (!endDate || !contractStartDate) return true; // Skip if either is empty

      const start = new Date(contractStartDate);
      const end = new Date(endDate);

      return end > start;
    }),

  defaultDiscountRate: yup
    .number()
    .nullable()
    .transform((value, originalValue) => {
      if (originalValue === '' || originalValue === null || originalValue === undefined) {
        return 0;
      }
      return value;
    })
    .min(0, 'نسبة الخصم يجب أن تكون 0 أو أكثر')
    .max(100, 'نسبة الخصم يجب ألا تتجاوز 100'),

  active: yup.boolean().default(true),

  allowAllEmployers: yup.boolean().default(false)
});

/**
 * Account creation form schema (for CREATE mode)
 */
export const accountCreationSchema = yup.object({
  username: yup
    .string()
    .required('اسم المستخدم مطلوب')
    .min(3, 'اسم المستخدم يجب أن يكون 3 أحرف على الأقل')
    .max(50, 'اسم المستخدم يجب ألا يتجاوز 50 حرف')
    .matches(/^[a-z0-9_]+$/, 'اسم المستخدم يجب أن يحتوي على أحرف صغيرة وأرقام وشرطة سفلية فقط'),

  fullName: yup
    .string()
    .nullable()
    .transform((value) => (value === '' ? null : value))
    .max(100, 'الاسم الكامل يجب ألا يتجاوز 100 حرف'),

  password: yup
    .string()
    .required('كلمة المرور مطلوبة')
    .min(PASSWORD_MIN_LENGTH, `كلمة المرور يجب أن تكون ${PASSWORD_MIN_LENGTH} أحرف على الأقل`)
    .matches(PASSWORD_REGEX, 'كلمة المرور يجب أن تحتوي على حرف كبير وحرف صغير ورقم واحد على الأقل'),

  confirmPassword: yup
    .string()
    .required('تأكيد كلمة المرور مطلوب')
    .oneOf([yup.ref('password')], 'كلمة المرور غير متطابقة')
});

/**
 * Default values for new provider
 */
export const providerDefaultValues = {
  name: '',
  licenseNumber: '',
  taxNumber: '',
  providerType: '',
  networkStatus: 'IN_NETWORK',
  city: '',
  address: '',
  phone: '',
  email: '',
  contractStartDate: null,
  contractEndDate: null,
  defaultDiscountRate: 0,
  active: true,
  allowAllEmployers: false
};

/**
 * Default values for account creation
 */
export const accountCreationDefaultValues = {
  username: '',
  fullName: '',
  password: '',
  confirmPassword: ''
};

/**
 * Helper: Convert empty strings to null for backend
 * Backend expects null for optional fields, not empty strings
 */
export const sanitizeProviderPayload = (data) => {
  return Object.fromEntries(
    Object.entries(data).map(([key, value]) => {
      if (value === '') return [key, null];
      if (typeof value === 'number' && key === 'defaultDiscountRate') {
        return [key, Number(value)];
      }
      return [key, value];
    })
  );
};

/**
 * Helper: Validate provider type
 */
export const isValidProviderType = (type) => {
  const validTypes = ['HOSPITAL', 'CLINIC', 'LAB', 'PHARMACY', 'OTHER'];
  return validTypes.includes(type);
};

/**
 * Helper: Get password strength indicator
 */
export const getPasswordStrength = (password) => {
  if (!password) return { strength: 0, label: '' };

  let strength = 0;
  const checks = {
    length: password.length >= PASSWORD_MIN_LENGTH,
    lowercase: /[a-z]/.test(password),
    uppercase: /[A-Z]/.test(password),
    number: /\d/.test(password),
    special: /[!@#$%^&*(),.?":{}|<>]/.test(password)
  };

  if (checks.length) strength += 20;
  if (checks.lowercase) strength += 20;
  if (checks.uppercase) strength += 20;
  if (checks.number) strength += 20;
  if (checks.special) strength += 20;

  let label = '';
  if (strength < 40) label = 'ضعيفة جداً';
  else if (strength < 60) label = 'ضعيفة';
  else if (strength < 80) label = 'متوسطة';
  else if (strength < 100) label = 'قوية';
  else label = 'قوية جداً';

  return { strength, label, checks };
};
