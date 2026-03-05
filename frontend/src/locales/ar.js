/**
 * Arabic Translations - نظام وعد
 * Phase D1.5 - Standardized Insurance Domain Terminology
 *
 * 🔒 These terms are LOCKED and should NOT be changed after this phase.
 */

const ar = {
  // ==================== NAVIGATION ====================
  nav: {
    dashboard: 'لوحة التحكم',
    members: 'المستفيدين',
    employers: 'جهات التعاقد',
    providers: 'مقدمو الخدمة الصحية',
    claims: 'المطالبات',
    visits: 'الزيارات الطبية',
    preApprovals: 'الموافقات المسبقة',
    medicalCategories: 'التصنيفات الطبية',
    medicalServices: 'الخدمات الطبية',
    medicalPackages: 'الباقات الطبية',
    benefitPackages: 'باقات المنافع',
    providerContracts: 'عقود مقدمي الخدمة',
    policies: 'وثائق التأمين',
    settings: 'إعدادات النظام',
    audit: 'سجل التدقيق',
    rbac: 'الصلاحيات',
    profile: 'الملف الشخصي',
    logout: 'تسجيل الخروج'
  },

  // ==================== MENU GROUPS ====================
  groups: {
    main: 'الرئيسية',
    dataManagement: 'إدارة البيانات',
    claimsManagement: 'إدارة المطالبات',
    medicalManagement: 'الإدارة الطبية',
    contractsDocuments: 'العقود والوثائق',
    settings: 'الإعدادات'
  },

  // ==================== COMMON ACTIONS ====================
  actions: {
    add: 'إضافة',
    edit: 'تعديل',
    delete: 'حذف',
    view: 'عرض',
    save: 'حفظ',
    cancel: 'إلغاء',
    search: 'بحث',
    filter: 'تصفية',
    export: 'تصدير',
    import: 'استيراد',
    refresh: 'تحديث',
    back: 'رجوع',
    next: 'التالي',
    previous: 'السابق',
    submit: 'إرسال',
    approve: 'موافقة',
    reject: 'رفض',
    pending: 'قيد الانتظار'
  },

  // ==================== COMMON LABELS ====================
  common: {
    name: 'الاسم',
    name: 'الاسم بالعربية',
    name: 'الاسم بالإنجليزية',
    code: 'الرمز',
    status: 'الحالة',
    active: 'نشط',
    inactive: 'غير نشط',
    date: 'التاريخ',
    startDate: 'تاريخ البداية',
    endDate: 'تاريخ الانتهاء',
    createdAt: 'تاريخ الإنشاء',
    updatedAt: 'تاريخ التحديث',
    description: 'الوصف',
    notes: 'ملاحظات',
    amount: 'المبلغ',
    total: 'الإجمالي',
    phone: 'الهاتف',
    email: 'البريد الإلكتروني',
    address: 'العنوان',
    type: 'النوع',
    category: 'التصنيف',
    details: 'التفاصيل',
    attachments: 'المرفقات',
    noData: 'لا توجد بيانات',
    loading: 'جاري التحميل...',
    error: 'حدث خطأ',
    success: 'تمت العملية بنجاح',
    confirm: 'تأكيد',
    warning: 'تحذير',
    required: 'مطلوب',
    optional: 'اختياري',
    all: 'الكل',
    select: 'اختر',
    none: 'لا يوجد'
  },

  // ==================== MEMBERS MODULE ====================
  members: {
    title: 'المستفيدين',
    singular: 'المستفيد',
    list: 'قائمة المستفيدين',
    add: 'إضافة مستفيد',
    edit: 'تعديل بيانات المستفيد',
    view: 'عرض بيانات المستفيد',
    nationalNumber: 'الرقم الوطني',
    cardNumber: 'رقم البطاقة',
    fullName: 'الاسم الكامل',
    birthDate: 'تاريخ الميلاد',
    gender: 'الجنس',
    male: 'ذكر',
    female: 'أنثى',
    nationality: 'الجنسية',
    maritalStatus: 'الحالة الاجتماعية',
    employer: 'جهة التعاقد',
    benefitPackage: 'باقة المنافع',
    policyNumber: 'رقم الوثيقة',
    membershipPeriod: 'فترة العضوية',
    familyMembers: 'أفراد العائلة',
    principalMember: 'العضو الرئيسي',
    dependent: 'تابع'
  },

  // ==================== EMPLOYERS MODULE ====================
  employers: {
    title: 'جهات التعاقد',
    singular: 'جهة تعاقد',
    list: 'قائمة جهات التعاقد',
    add: 'إضافة جهة تعاقد',
    edit: 'تعديل بيانات جهة التعاقد',
    view: 'عرض بيانات جهة التعاقد',
    contactPerson: 'الشخص المسؤول',
    contractNumber: 'رقم العقد',
    membersCount: 'عدد المستفيدين'
  },

  // ==================== PROVIDERS MODULE ====================
  providers: {
    title: 'مقدمو الخدمة الصحية',
    singular: 'مقدم خدمة صحية',
    list: 'قائمة مقدمي الخدمة الصحية',
    add: 'إضافة مقدم خدمة صحية',
    edit: 'تعديل بيانات مقدم الخدمة',
    view: 'عرض بيانات مقدم الخدمة',
    providerType: 'نوع مقدم الخدمة',
    hospital: 'مستشفى',
    clinic: 'عيادة',
    pharmacy: 'صيدلية',
    laboratory: 'مختبر',
    specialty: 'التخصص',
    license: 'رقم الترخيص'
  },

  // ==================== CLAIMS MODULE ====================
  claims: {
    title: 'المطالبات',
    singular: 'مطالبة',
    list: 'قائمة المطالبات',
    add: 'إضافة مطالبة',
    edit: 'تعديل المطالبة',
    view: 'عرض المطالبة',
    claimNumber: 'رقم المطالبة',
    claimDate: 'تاريخ المطالبة',
    claimAmount: 'مبلغ المطالبة',
    approvedAmount: 'المبلغ المعتمد',
    status: {
      pending: 'قيد المراجعة',
      approved: 'معتمدة',
      rejected: 'مرفوضة',
      paid: 'مدفوعة',
      partiallyPaid: 'مدفوعة جزئياً'
    }
  },

  // ==================== VISITS MODULE ====================
  visits: {
    title: 'الزيارات الطبية',
    singular: 'زيارة طبية',
    list: 'قائمة الزيارات الطبية',
    add: 'إضافة زيارة طبية',
    edit: 'تعديل الزيارة الطبية',
    view: 'عرض الزيارة الطبية',
    visitDate: 'تاريخ الزيارة',
    visitType: 'نوع الزيارة',
    diagnosis: 'التشخيص',
    treatment: 'العلاج',
    doctor: 'الطبيب المعالج'
  },

  // ==================== PRE-APPROVALS MODULE ====================
  preApprovals: {
    title: 'الموافقات المسبقة',
    singular: 'موافقة مسبقة',
    list: 'قائمة الموافقات المسبقة',
    add: 'طلب موافقة مسبقة',
    edit: 'تعديل طلب الموافقة',
    view: 'عرض طلب الموافقة',
    requestNumber: 'رقم الطلب',
    requestDate: 'تاريخ الطلب',
    procedure: 'الإجراء المطلوب',
    estimatedCost: 'التكلفة التقديرية',
    status: {
      pending: 'قيد المراجعة',
      approved: 'موافق عليه',
      rejected: 'مرفوض',
      expired: 'منتهي الصلاحية'
    }
  },

  // ==================== MEDICAL CATEGORIES ====================
  medicalCategories: {
    title: 'التصنيفات الطبية',
    singular: 'تصنيف طبي',
    list: 'قائمة التصنيفات الطبية',
    add: 'إضافة تصنيف طبي',
    edit: 'تعديل التصنيف الطبي',
    view: 'عرض التصنيف الطبي'
  },

  // ==================== MEDICAL SERVICES ====================
  medicalServices: {
    title: 'الخدمات الطبية',
    singular: 'خدمة طبية',
    list: 'قائمة الخدمات الطبية',
    add: 'إضافة خدمة طبية',
    edit: 'تعديل الخدمة الطبية',
    view: 'عرض الخدمة الطبية',
    serviceCode: 'رمز الخدمة',
    servicePrice: 'سعر الخدمة',
    coveragePercentage: 'نسبة التغطية'
  },

  // ==================== MEDICAL PACKAGES ====================
  medicalPackages: {
    title: 'الباقات الطبية',
    singular: 'باقة طبية',
    list: 'قائمة الباقات الطبية',
    add: 'إضافة باقة طبية',
    edit: 'تعديل الباقة الطبية',
    view: 'عرض الباقة الطبية',
    includedServices: 'الخدمات المشمولة'
  },

  // ==================== BENEFIT PACKAGES ====================
  benefitPackages: {
    title: 'باقات المنافع',
    singular: 'باقة منافع',
    list: 'قائمة باقات المنافع',
    add: 'إضافة باقة منافع',
    edit: 'تعديل باقة المنافع',
    view: 'عرض باقة المنافع',
    coverageLimit: 'حد التغطية',
    annualLimit: 'الحد السنوي',
    deductible: 'التحمل'
  },

  // ==================== POLICIES ====================
  policies: {
    title: 'وثائق التأمين',
    singular: 'وثيقة تأمين',
    list: 'قائمة وثائق التأمين',
    add: 'إضافة وثيقة تأمين',
    edit: 'تعديل وثيقة التأمين',
    view: 'عرض وثيقة التأمين',
    policyNumber: 'رقم الوثيقة',
    effectiveDate: 'تاريخ السريان',
    expiryDate: 'تاريخ الانتهاء'
  },

  // ==================== SETTINGS ====================
  settings: {
    title: 'إعدادات النظام',
    general: 'الإعدادات العامة',
    theme: 'المظهر',
    language: 'اللغة',
    notifications: 'الإشعارات',
    security: 'الأمان'
  },

  // ==================== AUDIT ====================
  audit: {
    title: 'سجل التدقيق',
    action: 'الإجراء',
    user: 'المستخدم',
    timestamp: 'التوقيت',
    details: 'التفاصيل',
    ipAddress: 'عنوان IP'
  },

  // ==================== TPA SYSTEM ====================
  // NOTE: InsuranceCompany section removed - System operates as TPA (شركة وعد)
  tpa: {
    name: 'شركة وعد',
    systemName: 'نظام إدارة التأمين الصحي'
  },

  // ==================== AUTH ====================
  auth: {
    login: 'تسجيل الدخول',
    logout: 'تسجيل الخروج',
    username: 'اسم المستخدم',
    password: 'كلمة المرور',
    rememberMe: 'تذكرني',
    forgotPassword: 'نسيت كلمة المرور؟',
    profile: 'الملف الشخصي',
    changePassword: 'تغيير كلمة المرور'
  },

  // ==================== PERMISSIONS (RBAC) ====================
  // 🔒 ترجمات الصلاحيات - ثابتة ولا تعتمد على قاعدة البيانات
  permissions: {
    // لوحة التحكم
    VIEW_DASHBOARD: { name: 'عرض لوحة التحكم', desc: 'الوصول إلى لوحة التحكم الرئيسية' },

    // المستفيدين
    VIEW_MEMBERS: { name: 'عرض المستفيدين', desc: 'عرض قائمة وبيانات المستفيدين' },
    MANAGE_MEMBERS: { name: 'إدارة المستفيدين', desc: 'إضافة وتعديل وحذف المستفيدين' },
    IMPORT_MEMBERS: { name: 'استيراد المستفيدين', desc: 'استيراد بيانات المستفيدين من ملفات Excel' },
    EXPORT_MEMBERS: { name: 'تصدير المستفيدين', desc: 'تصدير بيانات المستفيدين إلى ملفات Excel' },

    // جهات العمل
    VIEW_EMPLOYERS: { name: 'عرض جهات العمل', desc: 'عرض قائمة جهات العمل المتعاقدة' },
    MANAGE_EMPLOYERS: { name: 'إدارة جهات العمل', desc: 'إضافة وتعديل وحذف جهات العمل' },

    // مقدمي الخدمات
    VIEW_PROVIDERS: { name: 'عرض مقدمي الخدمات', desc: 'عرض المستشفيات والعيادات المتعاقدة' },
    MANAGE_PROVIDERS: { name: 'إدارة مقدمي الخدمات', desc: 'إضافة وتعديل وحذف مقدمي الخدمات' },

    // المطالبات
    VIEW_CLAIMS: { name: 'عرض المطالبات', desc: 'عرض قائمة المطالبات المالية' },
    MANAGE_CLAIMS: { name: 'إدارة المطالبات', desc: 'إنشاء وتعديل المطالبات' },
    REVIEW_CLAIMS: { name: 'مراجعة المطالبات', desc: 'مراجعة المطالبات طبياً' },
    APPROVE_CLAIMS: { name: 'اعتماد المطالبات', desc: 'الموافقة المالية على المطالبات' },

    // الموافقات المسبقة
    VIEW_PREAPPROVALS: { name: 'عرض الموافقات المسبقة', desc: 'عرض طلبات الموافقة المسبقة' },
    MANAGE_PREAPPROVALS: { name: 'إدارة الموافقات المسبقة', desc: 'إنشاء وتعديل طلبات الموافقة' },
    REVIEW_PREAPPROVALS: { name: 'مراجعة الموافقات المسبقة', desc: 'المراجعة الطبية للموافقات' },
    APPROVE_PREAPPROVALS: { name: 'اعتماد الموافقات المسبقة', desc: 'الموافقة المالية على الطلبات' },

    // الخدمات الطبية
    VIEW_MEDICAL_SERVICES: { name: 'عرض الخدمات الطبية', desc: 'عرض قائمة الخدمات الطبية' },
    MANAGE_MEDICAL_SERVICES: { name: 'إدارة الخدمات الطبية', desc: 'إضافة وتعديل الخدمات الطبية' },
    VIEW_MEDICAL_CATEGORIES: { name: 'عرض التصنيفات الطبية', desc: 'عرض تصنيفات الخدمات' },
    MANAGE_MEDICAL_CATEGORIES: { name: 'إدارة التصنيفات الطبية', desc: 'إضافة وتعديل التصنيفات' },

    // سياسات المنافع
    VIEW_BENEFIT_POLICIES: { name: 'عرض سياسات المنافع', desc: 'عرض سياسات وحدود التغطية' },
    MANAGE_BENEFIT_POLICIES: { name: 'إدارة سياسات المنافع', desc: 'تعديل سياسات التغطية' },

    // المستخدمين والأدوار
    VIEW_USERS: { name: 'عرض المستخدمين', desc: 'عرض قائمة مستخدمي النظام' },
    MANAGE_USERS: { name: 'إدارة المستخدمين', desc: 'إضافة وتعديل المستخدمين' },
    VIEW_ROLES: { name: 'عرض الأدوار', desc: 'عرض أدوار النظام' },
    MANAGE_ROLES: { name: 'إدارة الأدوار', desc: 'إنشاء وتعديل الأدوار والصلاحيات' },

    // التقارير
    VIEW_REPORTS: { name: 'عرض التقارير', desc: 'الوصول إلى التقارير والإحصائيات' },
    EXPORT_REPORTS: { name: 'تصدير التقارير', desc: 'تصدير التقارير إلى ملفات' },

    // الإعدادات
    VIEW_SETTINGS: { name: 'عرض الإعدادات', desc: 'عرض إعدادات النظام' },
    MANAGE_SYSTEM_SETTINGS: { name: 'إدارة إعدادات النظام', desc: 'تعديل إعدادات النظام' },

    // الزيارات
    VIEW_VISITS: { name: 'عرض الزيارات', desc: 'عرض سجل الزيارات الطبية' },
    MANAGE_VISITS: { name: 'إدارة الزيارات', desc: 'تسجيل وتعديل الزيارات' }
  },

  // ==================== ROLES ====================
  roles: {
    SUPER_ADMIN: { name: 'مدير النظام', desc: 'صلاحيات كاملة على النظام' },
    MEDICAL_REVIEWER: { name: 'المراجع الطبي', desc: 'مراجعة المطالبات والموافقات' },
    ACCOUNTANT: { name: 'المحاسب المالي', desc: 'الاعتماد المالي للمطالبات' },
    EMPLOYER_ADMIN: { name: 'مدير جهة العمل', desc: 'إدارة موظفي الشركة' },
    PROVIDER: { name: 'مقدم الخدمة', desc: 'إدارة المطالبات والخدمات' },
    MEMBER: { name: 'المستفيد', desc: 'عرض بيانات التأمين الشخصية' },
    USER: { name: 'مستخدم عادي', desc: 'صلاحيات محدودة للعرض فقط' }
  },

  // ==================== SYSTEM ====================
  system: {
    name: 'نظام وعد',
    subtitle: 'إدارة مطالبات التأمين',
    copyright: '© {year} نظام Lymed (لايميد) لإدارة النفقات الطبية - جميع الحقوق محفوظة',
    version: 'الإصدار'
  }
};

export default ar;
