/**
 * Insurance Terminology Constants
 * Standardized Arabic/English terms for insurance domain
 *
 * Usage:
 * import { INSURANCE_TERMS, getTerminology } from 'components/insurance';
 * const term = getTerminology('member', 'ar'); // 'المؤمَّن عليه'
 */

export const INSURANCE_TERMS = {
  // Core Entities
  member: {
    ar: 'المؤمَّن عليه',
    en: 'Insured Member'
  },
  principal: {
    ar: 'الموظف',
    en: 'Primary Insured'
  },
  dependent: {
    ar: 'التابع',
    en: 'Dependent'
  },
  dependents: {
    ar: 'التابعين',
    en: 'Dependents'
  },
  policy: {
    ar: 'وثيقة التأمين',
    en: 'Insurance Policy'
  },
  policyNumber: {
    ar: 'رقم البوليصة',
    en: 'Policy Number'
  },
  claim: {
    ar: 'المطالبة',
    en: 'Claim'
  },
  preApproval: {
    ar: 'الموافقة المسبقة',
    en: 'Prior Authorization'
  },
  authorization: {
    ar: 'التفويض الطبي',
    en: 'Medical Authorization'
  },
  provider: {
    ar: 'مقدم الخدمة الصحية',
    en: 'Healthcare Provider'
  },
  benefitPackage: {
    ar: 'باقة المنافع',
    en: 'Benefit Plan'
  },
  medicalService: {
    ar: 'الخدمة الطبية',
    en: 'Medical Service'
  },

  // Financial Terms
  coverageLimit: {
    ar: 'الحد الأقصى للتغطية',
    en: 'Maximum Coverage'
  },
  copay: {
    ar: 'نسبة التحمل',
    en: 'Co-payment'
  },
  copayPercentage: {
    ar: 'نسبة التحمل',
    en: 'Co-pay Percentage'
  },
  deductible: {
    ar: 'مبلغ التحمل',
    en: 'Deductible'
  },
  requestedAmount: {
    ar: 'المبلغ المطلوب',
    en: 'Requested Amount'
  },
  approvedAmount: {
    ar: 'المبلغ الموافق عليه',
    en: 'Approved Amount'
  },
  rejectedAmount: {
    ar: 'المبلغ المرفوض',
    en: 'Rejected Amount'
  },
  insurerPays: {
    ar: 'تدفع الشركة',
    en: 'Company Pays'
  },
  memberPays: {
    ar: 'يدفع المؤمَّن عليه',
    en: 'Member Pays'
  },
  premium: {
    ar: 'قسط التأمين',
    en: 'Premium'
  },

  // Network Terms
  network: {
    ar: 'شبكة مقدمي الخدمة',
    en: 'Provider Network'
  },
  inNetwork: {
    ar: 'ضمن الشبكة',
    en: 'In-Network'
  },
  outOfNetwork: {
    ar: 'خارج الشبكة',
    en: 'Out-of-Network'
  },
  preferredNetwork: {
    ar: 'الشبكة المفضلة',
    en: 'Preferred Network'
  },
  networkTier: {
    ar: 'مستوى الشبكة',
    en: 'Network Tier'
  },

  // Date Terms
  effectiveDate: {
    ar: 'تاريخ سريان التغطية',
    en: 'Coverage Start Date'
  },
  expiryDate: {
    ar: 'تاريخ انتهاء التغطية',
    en: 'Coverage End Date'
  },
  coveragePeriod: {
    ar: 'فترة التغطية',
    en: 'Coverage Period'
  },
  validUntil: {
    ar: 'صالحة حتى',
    en: 'Valid Until'
  },
  visitDate: {
    ar: 'تاريخ الزيارة الطبية',
    en: 'Visit Date'
  },

  // Status Terms
  active: {
    ar: 'نشط',
    en: 'Active'
  },
  inactive: {
    ar: 'غير نشط',
    en: 'Inactive'
  },
  suspended: {
    ar: 'موقوف',
    en: 'Suspended'
  },
  terminated: {
    ar: 'منتهي',
    en: 'Terminated'
  },
  pending: {
    ar: 'قيد الانتظار',
    en: 'Pending'
  },
  approved: {
    ar: 'موافق عليه',
    en: 'Approved'
  },
  rejected: {
    ar: 'مرفوض',
    en: 'Rejected'
  },
  expired: {
    ar: 'منتهي الصلاحية',
    en: 'Expired'
  },

  // Claim Status Terms
  pendingReview: {
    ar: 'قيد المراجعة',
    en: 'Pending Review'
  },
  underReview: {
    ar: 'تحت المراجعة',
    en: 'Under Review'
  },
  partiallyApproved: {
    ar: 'موافق عليه جزئياً',
    en: 'Partially Approved'
  },
  returnedForInfo: {
    ar: 'مطلوب مستندات إضافية',
    en: 'Additional Documents Required'
  },
  cancelled: {
    ar: 'ملغاة',
    en: 'Cancelled'
  },

  // Priority Terms
  routine: {
    ar: 'عادي',
    en: 'Routine'
  },
  urgent: {
    ar: 'عاجل',
    en: 'Urgent'
  },
  emergency: {
    ar: 'طارئ',
    en: 'Emergency'
  },

  // Visit Types
  scheduled: {
    ar: 'مجدولة',
    en: 'Scheduled'
  },
  walkIn: {
    ar: 'حضور مباشر',
    en: 'Walk-in'
  },
  followUp: {
    ar: 'متابعة',
    en: 'Follow-up'
  },
  emergencyVisit: {
    ar: 'زيارة طوارئ',
    en: 'Emergency Visit'
  },

  // Card Status
  cardActive: {
    ar: 'البطاقة نشطة',
    en: 'Card Active'
  },
  cardSuspended: {
    ar: 'البطاقة موقوفة',
    en: 'Card Suspended'
  },
  cardBlocked: {
    ar: 'البطاقة محظورة',
    en: 'Card Blocked'
  },
  cardExpired: {
    ar: 'البطاقة منتهية',
    en: 'Card Expired'
  },

  // Relationships
  wife: {
    ar: 'زوجة',
    en: 'Wife'
  },
  husband: {
    ar: 'زوج',
    en: 'Husband'
  },
  son: {
    ar: 'ابن',
    en: 'Son'
  },
  daughter: {
    ar: 'ابنة',
    en: 'Daughter'
  },
  father: {
    ar: 'أب',
    en: 'Father'
  },
  mother: {
    ar: 'أم',
    en: 'Mother'
  },
  brother: {
    ar: 'أخ',
    en: 'Brother'
  },
  sister: {
    ar: 'أخت',
    en: 'Sister'
  },

  // Policy Types
  groupPolicy: {
    ar: 'بوليصة جماعية',
    en: 'Group Policy'
  },
  individualPolicy: {
    ar: 'بوليصة فردية',
    en: 'Individual Policy'
  },
  corporatePolicy: {
    ar: 'بوليصة شركات',
    en: 'Corporate Policy'
  },

  // Actions
  convertToClaim: {
    ar: 'تحويل إلى مطالبة',
    en: 'Convert to Claim'
  },
  renewPolicy: {
    ar: 'تجديد البوليصة',
    en: 'Renew Policy'
  },
  requestPreApproval: {
    ar: 'طلب موافقة مسبقة',
    en: 'Request Pre-Approval'
  },
  viewDetails: {
    ar: 'عرض التفاصيل',
    en: 'View Details'
  },

  // Section Headers
  personalInfo: {
    ar: 'المعلومات الشخصية',
    en: 'Personal Information'
  },
  contactInfo: {
    ar: 'معلومات الاتصال',
    en: 'Contact Information'
  },
  employmentInfo: {
    ar: 'معلومات العمل',
    en: 'Employment Information'
  },
  insuranceInfo: {
    ar: 'معلومات التأمين',
    en: 'Insurance Information'
  },
  medicalInfo: {
    ar: 'المعلومات الطبية',
    en: 'Medical Information'
  },
  financialInfo: {
    ar: 'المعلومات المالية',
    en: 'Financial Information'
  },
  financialSummary: {
    ar: 'الملخص المالي',
    en: 'Financial Summary'
  },

  // Misc
  daysRemaining: {
    ar: 'يوم متبقي',
    en: 'days remaining'
  },
  renewalSoon: {
    ar: 'موعد التجديد قريب',
    en: 'Renewal Due Soon'
  },
  requiresPreApproval: {
    ar: 'يتطلب موافقة مسبقة',
    en: 'Requires Pre-Approval'
  },
  noPreApprovalRequired: {
    ar: 'لا يتطلب موافقة مسبقة',
    en: 'No Pre-Approval Required'
  }
};

/**
 * Get terminology in specified language
 * @param {string} key - Term key
 * @param {string} language - 'ar' or 'en'
 * @returns {string} - Translated term
 */
export const getTerminology = (key, language = 'ar') => {
  const term = INSURANCE_TERMS[key];
  if (!term) {
    console.warn(`Insurance term not found: ${key}`);
    return key;
  }
  return term[language] || term.ar || key;
};

/**
 * Get all terms for a language
 * @param {string} language - 'ar' or 'en'
 * @returns {Object} - All terms in specified language
 */
export const getAllTerms = (language = 'ar') => {
  const terms = {};
  Object.keys(INSURANCE_TERMS).forEach((key) => {
    terms[key] = INSURANCE_TERMS[key][language] || INSURANCE_TERMS[key].ar;
  });
  return terms;
};

export default INSURANCE_TERMS;
