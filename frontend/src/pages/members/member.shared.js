// Relationship Translation Map
export const RELATIONSHIP_AR = {
  WIFE: 'زوجة',
  HUSBAND: 'زوج',
  SON: 'ابن',
  DAUGHTER: 'ابنة',
  FATHER: 'أب',
  MOTHER: 'أم',
  BROTHER: 'أخ',
  SISTER: 'أخت'
};

// MUI Select menu sizing, identical across every member form (Create/Edit).
// Kept here so a future style tweak only needs one edit.
export const MEMBER_FORM_MENU_PROPS = {
  PaperProps: {
    sx: {
      '& .MuiMenuItem-root': { fontSize: '0.75rem' },
      maxHeight: '18.75rem',
      minWidth: '12.5rem'
    }
  }
};

// Fields restricted to digits-only, with their max length -- identical rule
// in UnifiedMemberCreate and UnifiedMemberEdit before this extraction (each
// carried its own copy of the same regex/length pair).
const DIGIT_ONLY_FIELD_MAX_LENGTH = {
  nationalNumber: 12,
  phone: 10,
  employeeNumber: null // digits-only, no length cap
};

/**
 * Applies the member form's digit-only input restriction (nationalNumber,
 * phone, employeeNumber): strips non-digit characters and caps length where
 * a cap applies. Returns the original value unchanged for every other field
 * or non-string value, so callers can run every field's onChange through
 * this without an extra branch.
 *
 * @param {string} field the form field name being changed
 * @param {*} value the incoming value (string from a text input, or
 *                   anything else from a DatePicker/Select)
 * @returns {*} the sanitized value to store, or the original value/length-cap
 *              sentinel {@link SANITIZED_VALUE_REJECTED} meaning "ignore this
 *              keystroke, don't update state"
 */
export function sanitizeMemberFieldValue(field, value) {
  if (typeof value !== 'string' || !(field in DIGIT_ONLY_FIELD_MAX_LENGTH)) {
    return { accepted: true, value };
  }
  const digitsOnly = value.replace(/\D/g, '');
  const maxLength = DIGIT_ONLY_FIELD_MAX_LENGTH[field];
  if (maxLength != null && digitsOnly.length > maxLength) {
    return { accepted: false, value: undefined };
  }
  return { accepted: true, value: digitsOnly };
}

// National number: optional, but when present must be exactly 12 digits.
// Same rule, same message, in both Create and Edit before this extraction.
export function validateNationalNumber(value) {
  if (value && value.length !== 12) {
    return 'الرقم الوطني يجب أن يتكون من 12 خانة';
  }
  return null;
}

// Libyan mobile format: 09x followed by 7 digits (10 digits total).
// `message` lets each screen keep its own existing wording (Create's longer
// hint vs Edit's shorter one) while sharing the one regex both already used.
export function validateLibyanPhone(
  value,
  { required = false, message = 'رقم الهاتف غير صحيح (يجب أن يبدأ بـ 09x ويتكون من 10 أرقام)' } = {}
) {
  if (!value) {
    return required ? 'رقم الهاتف مطلوب' : null;
  }
  if (!/^(091|092|094|093|095|096)\d{7}$/.test(value)) {
    return message;
  }
  return null;
}
