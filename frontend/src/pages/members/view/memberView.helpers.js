// Shared helpers for the UnifiedMemberView page and its tab components.
// Extracted so the member/dependent status chip label+color mapping has one
// definition instead of two byte-identical copies (was duplicated between
// the principal header and the dependents table).

export const unwrapApi = (response) => response?.data?.data ?? response?.data ?? response;

export const toArray = (payload) => {
  const value = unwrapApi(payload);
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.items)) return value.items;
  if (Array.isArray(value?.content)) return value.content;
  if (Array.isArray(value?.data)) return value.data;
  return [];
};

export const formatMoney = (value) => {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? `${numeric.toFixed(2)} د.ل` : '-';
};

export const MEDICAL_HISTORY_STATUS_LABELS = {
  APPROVED: 'معتمد',
  REJECTED: 'مرفوض',
  PENDING: 'معلق',
  SUBMITTED: 'مرسل',
  RESUBMITTED: 'معاد إرساله',
  UNDER_REVIEW: 'قيد المراجعة',
  APPROVAL_IN_PROGRESS: 'قيد الاعتماد',
  ACKNOWLEDGED: 'تم الاطلاع',
  NEEDS_CORRECTION: 'يحتاج تصحيح',
  CANCELLED: 'ملغى',
  EXPIRED: 'منتهي',
  USED: 'مستخدم',
  REGISTERED: 'مسجلة',
  IN_PROGRESS: 'قيد التنفيذ',
  COMPLETED: 'مكتملة',
  CLOSED: 'مغلقة'
};

export const medicalHistoryStatusLabel = (status) => MEDICAL_HISTORY_STATUS_LABELS[status] || status || '-';

const MEDICAL_HISTORY_STATUS_COLORS = {
  APPROVED: 'success',
  COMPLETED: 'success',
  CLOSED: 'success',
  REJECTED: 'error',
  CANCELLED: 'error',
  EXPIRED: 'error',
  UNDER_REVIEW: 'warning',
  APPROVAL_IN_PROGRESS: 'warning',
  NEEDS_CORRECTION: 'warning',
  PENDING: 'info',
  SUBMITTED: 'info',
  RESUBMITTED: 'info'
};

export const medicalHistoryStatusColor = (status) => MEDICAL_HISTORY_STATUS_COLORS[status] || 'default';

export const MEMBER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'نشط' },
  { value: 'SUSPENDED', label: 'موقوف' },
  { value: 'PENDING', label: 'قيد المراجعة' },
  { value: 'TERMINATED', label: 'منتهي' }
];

const MEMBER_STATUS_LABELS = { ACTIVE: 'نشط', TERMINATED: 'منتهي', SUSPENDED: 'موقوف', PENDING: 'قيد المراجعة' };
const MEMBER_STATUS_COLORS = { ACTIVE: 'success', TERMINATED: 'error', SUSPENDED: 'warning', PENDING: 'warning' };

export const memberStatusLabel = (status) => MEMBER_STATUS_LABELS[status] || status;
export const memberStatusColor = (status) => MEMBER_STATUS_COLORS[status] || 'default';
