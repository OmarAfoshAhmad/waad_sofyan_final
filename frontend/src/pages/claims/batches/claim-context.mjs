export const normalizeClaimServiceContext = (value) => {
  const normalized = String(value || '').trim().toUpperCase();
  return /^[A-Z][A-Z0-9_]{1,59}$/.test(normalized) ? normalized : 'ANY';
};

export const getServiceContext = (service) =>
  normalizeClaimServiceContext(
    service?.encounterType ??
      service?.defaultEncounterType ??
      service?.serviceEncounterType ??
      service?.pricingEncounterType ??
      service?.contextType ??
      service?.context
  );

export const isServiceAllowedForClaimContext = (service, claimEncounterType) => {
  const claimContext = normalizeClaimServiceContext(claimEncounterType || 'OUTPATIENT');
  if (claimContext === 'ANY') return true;
  const serviceContext = getServiceContext(service);
  return serviceContext === 'ANY' || serviceContext === claimContext;
};

export const resolveClaimContextSelection = (contexts, code) => {
  const selected = (Array.isArray(contexts) ? contexts : []).find((context) => context.code === code);
  if (!selected) return null;
  return {
    claimContextCode: selected.code,
    encounterType: selected.baseEncounterType,
    fullCoverage: selected.code === 'FULL_COVERAGE'
  };
};
