export const DEFAULT_DATE_DISPLAY_FORMAT = 'dd/MM/yyyy';
export const ALLOWED_DATE_DISPLAY_FORMATS = ['dd/MM/yyyy', 'dd-MM-yyyy', 'yyyy-MM-dd'];

let activeDateDisplayFormat = DEFAULT_DATE_DISPLAY_FORMAT;

export const normalizeDateDisplayFormat = (value) =>
  ALLOWED_DATE_DISPLAY_FORMATS.includes(value) ? value : DEFAULT_DATE_DISPLAY_FORMAT;

export const setDateDisplayFormat = (value) => {
  activeDateDisplayFormat = normalizeDateDisplayFormat(value);
};

export const getDateDisplayFormat = () => activeDateDisplayFormat;

// MUI uses date-fns tokens, while the legacy GregorianDatePicker uses Day.js.
export const toDayjsDateDisplayFormat = (value = activeDateDisplayFormat) =>
  ({ 'dd/MM/yyyy': 'DD/MM/YYYY', 'dd-MM-yyyy': 'DD-MM-YYYY', 'yyyy-MM-dd': 'YYYY-MM-DD' })[
    normalizeDateDisplayFormat(value)
  ];

export const getDayjsDateDisplayFormat = () => toDayjsDateDisplayFormat(activeDateDisplayFormat);

export const formatDateParts = ({ day, month, year }, pattern = activeDateDisplayFormat) =>
  normalizeDateDisplayFormat(pattern)
    .replace('dd', String(day).padStart(2, '0'))
    .replace('MM', String(month).padStart(2, '0'))
    .replace('yyyy', String(year));
