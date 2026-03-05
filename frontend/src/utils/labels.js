/**
 * Static UI Labels for Arabic-first UX
 * TBA-WAAD Insurance System (Closed Enterprise)
 *
 * This file replaces react-intl dynamic i18n with static Arabic strings
 * for stability and performance in a closed enterprise environment.
 *
 * Usage:
 * import { LABELS } from 'utils/labels';
 * <Typography>{LABELS.common.save}</Typography>
 */

export const LABELS = {
  // Application
  app: {
    name: 'نظام الواحة للإدارة الطبية',
    shortName: 'الواحة كير',
    description: 'نظام إدارة شامل للتأمين والخدمات الطبية'
  },

  // Navigation
  nav: {
    dashboard: 'لوحة التحكم',
    members: 'الأعضاء',
    employers: 'جهات العمل',
    claims: 'المطالبات',
    providers: 'مقدمو الخدمة',
    providerContracts: 'عقود مقدمي الخدمة',
    visits: 'الزيارات',
    policies: 'البوليصات',
    benefitPackages: 'باقات المزايا',
    preApprovals: 'الموافقات المسبقة',
    insurancePolicies: 'بوليصات التأمين',
    medicalServices: 'الخدمات الطبية',
    medicalCategories: 'الفئات الطبية',
    medicalPackages: 'الباقات الطبية',
    companies: 'الشركات',
    reviewerCompanies: 'شركات المراجعة',
    admin: 'الإدارة',
    rbac: 'إدارة الصلاحيات',
    settings: 'الإعدادات',
    profile: 'الملف الشخصي',
    audit: 'سجل التدقيق',
    logout: 'تسجيل الخروج',
    systemAdmin: 'إدارة النظام',
    userManagement: 'إدارة المستخدمين',
    roleManagement: 'إدارة الأدوار',
    permissionMatrix: 'مصفوفة الصلاحيات',
    featureFlags: 'التحكم بالميزات',
    moduleAccess: 'التحكم بالوحدات',
    auditLog: 'سجل التدقيق الشامل',
    home: 'الرئيسية',
    viewMore: 'عرض المزيد'
  },

  // Common actions and labels
  common: {
    search: 'بحث',
    add: 'إضافة',
    edit: 'تعديل',
    delete: 'حذف',
    save: 'حفظ',
    cancel: 'إلغاء',
    submit: 'إرسال',
    actions: 'الإجراءات',
    status: 'الحالة',
    active: 'نشط',
    inactive: 'غير نشط',
    name: 'الاسم',
    email: 'البريد الإلكتروني',
    phone: 'الهاتف',
    address: 'العنوان',
    date: 'التاريخ',
    details: 'التفاصيل',
    description: 'الوصف',
    type: 'النوع',
    amount: 'المبلغ',
    total: 'الإجمالي',
    view: 'عرض',
    viewMore: 'عرض المزيد',
    noData: 'لا توجد بيانات',
    loading: 'جار التحميل',
    error: 'خطأ',
    success: 'نجاح',
    warning: 'تحذير',
    info: 'معلومة',
    confirm: 'تأكيد',
    yes: 'نعم',
    no: 'لا',
    ok: 'موافق',
    close: 'إغلاق',
    back: 'رجوع',
    next: 'التالي',
    previous: 'السابق',
    first: 'الأول',
    last: 'الأخير',
    page: 'صفحة',
    of: 'من',
    rowsPerPage: 'عدد الصفوف في الصفحة:',
    all: 'الكل',
    filter: 'تصفية',
    sort: 'ترتيب',
    export: 'تصدير',
    import: 'استيراد',
    print: 'طباعة',
    refresh: 'تحديث',
    required: 'مطلوب',
    invalid: 'غير صالح',
    saving: 'جار الحفظ...',
    deleting: 'جار الحذف...',
    saveChanges: 'حفظ التعديلات',
    backToList: 'رجوع إلى القائمة',
    id: 'رقم',
    createdAt: 'تاريخ الإنشاء',
    updatedAt: 'تاريخ التحديث'
  },

  // Validation messages
  validation: {
    required: 'هذا الحقل مطلوب',
    emailInvalid: 'البريد الإلكتروني غير صالح',
    phoneInvalid: 'رقم الهاتف غير صالح',
    minLength: 'الحد الأدنى للطول',
    maxLength: 'الحد الأقصى للطول',
    fixErrors: 'الرجاء تصحيح الأخطاء'
  },

  // Members module
  members: {
    list: 'المستفيدين',
    listDesc: 'إدارة المستفيدين من التأمين',
    add: 'إضافة مستفيد',
    edit: 'تعديل المستفيد',
    view: 'عرض المستفيد',
    search: 'البحث في المستفيدين...',
    fullName: 'الاسم الكامل',
    memberType: 'النوع',
    nationalNumber: 'الرقم الوطني',
    employer: 'جهة العمل',
    policyNumber: 'رقم البوليصة',
    cardStatus: 'حالة البطاقة',
    noFound: 'لم يتم العثور على مستفيدين',
    noFoundDesc: 'ابدأ بإضافة أول مستفيد',
    deleteConfirm: 'هل تريد حذف هذا المستفيد؟',
    bulkUpload: 'رفع Excel',
    bulkUploadInfo: 'قم برفع ملف Excel (.xlsx أو .xls) يحتوي على بيانات المستفيدين.',
    bulkUploadComingSoon: 'ميزة الرفع الجماعي قريباً!'
  },

  // Employers module
  employers: {
    list: 'جهات العمل',
    listDesc: 'إدارة جهات العمل ومعلوماتهم',
    add: 'إضافة جهة عمل',
    edit: 'تعديل جهة العمل',
    view: 'عرض جهة العمل',
    viewSubtitle: 'عرض معلومات جهة العمل',
    code: 'الرمز',
    employerCode: 'رمز جهة العمل',
    employerCodePlaceholder: 'أدخل رمز جهة العمل',
    name: 'الاسم (عربي)',
    namePlaceholder: 'أدخل الاسم بالعربية',
    nameEn: 'الاسم (إنجليزي)',
    nameEnPlaceholder: 'أدخل الاسم بالإنجليزية',
    noFound: 'لم يتم العثور على جهات عمل',
    noFoundDesc: 'ابدأ بإضافة أول جهة عمل',
    deleteConfirmTitle: 'حذف جهة العمل',
    deleteConfirm: 'هل أنت متأكد من حذف جهة العمل هذه؟',
    deletedSuccess: 'تم حذف جهة العمل بنجاح',
    createdSuccess: 'تم إنشاء جهة العمل بنجاح',
    updatedSuccess: 'تم تحديث جهة العمل بنجاح',
    notFound: 'لم يتم العثور على جهة العمل',
    loadError: 'فشل في تحميل جهة العمل',
    basicInfo: 'المعلومات الأساسية',
    additionalInfo: 'معلومات إضافية',
    policy: 'البوليصة',
    statistics: 'الإحصائيات',
    totalMembers: 'إجمالي المستفيدين',
    activePolicies: 'البوليصات النشطة',
    totalClaims: 'إجمالي المطالبات'
  },

  // File upload
  upload: {
    selectFile: 'الرجاء اختيار ملف أولاً',
    invalidFileType: 'الرجاء اختيار ملف Excel',
    clickToUpload: 'انقر لاختيار ملف Excel',
    supportedFormats: 'الصيغ المدعومة: .xlsx, .xls',
    fileSelected: 'تم اختيار الملف',
    size: 'الحجم'
  },

  // Dashboard
  dashboard: {
    welcome: 'مرحباً بك في نظام الواحة كير',
    welcomeDescription: 'نظام إدارة شامل للتأمين والخدمات الطبية مع ميزات متقدمة لإدارة الأعضاء والمطالبات والتقارير',
    viewStatistics: 'عرض الإحصائيات الكاملة'
  }
};

// Helper function to get nested label by key path (e.g., 'common.save')
export const getLabel = (keyPath) => {
  const keys = keyPath.split('.');
  let result = LABELS;
  for (const key of keys) {
    if (result && typeof result === 'object' && key in result) {
      result = result[key];
    } else {
      console.warn(`Label not found: ${keyPath}`);
      return keyPath;
    }
  }
  return result;
};

export default LABELS;
