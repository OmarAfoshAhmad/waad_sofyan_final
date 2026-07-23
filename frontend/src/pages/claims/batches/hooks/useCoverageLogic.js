import { useCallback, useEffect, useRef } from 'react';
import claimsService from 'services/api/claims.service';
import { failedCoverageResult, normalizeCoverageResult } from './coverageContract.mjs';

export function useCoverageLogic({
  policyId,
  member,
  medicalCategories,
  encounterType,
  recompute,
  currentClaimId,
  serviceYear,
  serviceDate,
  fullCoverage,
  onCoverageError
}) {
  const isDev = typeof import.meta !== 'undefined' && import.meta.env?.DEV;
  const singleRequestIdRef = useRef(0);
  const bulkRequestIdRef = useRef(0);
  const singleAbortRef = useRef(null);
  const bulkAbortRef = useRef(null);
  const diagnosticsRef = useRef({
    singleStarted: 0,
    singleAborted: 0,
    singleStaleIgnored: 0,
    bulkStarted: 0,
    bulkAborted: 0,
    bulkStaleIgnored: 0
  });

  const debugLog = useCallback(
    (event, extra = {}) => {
      if (!isDev) return;

      console.debug('[coverage-race-guard]', event, {
        ...diagnosticsRef.current,
        ...extra
      });
    },
    [isDev]
  );

  useEffect(() => {
    return () => {
      singleAbortRef.current?.abort();
      bulkAbortRef.current?.abort();
    };
  }, []);

  const toMoney = (value) => {
    const num = Number(value);
    return Number.isFinite(num) ? Number(num.toFixed(2)) : 0;
  };

  const toInt = (value, fallback = 0) => {
    const num = Number.parseInt(value, 10);
    return Number.isFinite(num) ? num : fallback;
  };

  const buildEngineLineInput = (line, idx) => {
    let serviceOwnCategoryId =
      line?.serviceCategoryId ??
      line?.medicalCategoryId ??
      line?.service?.categoryId ??
      line?.service?.serviceCategoryId ??
      line?.service?.medicalCategoryId ??
      line?.service?.medicalCategory?.id ??
      line?.service?.effectiveCategory?.id ??
      null;

    const code = line?.serviceCode || line?.service?.serviceCode || line?.service?.code;
    if (code === 'GEN-MEDICATION' || code === 'GEN-MEDICAL-SERVICE') {
      const targetCode = code === 'GEN-MEDICATION' ? 'CAT-DRUG' : 'CAT-DIAGNOSTIC';
      const foundCat = medicalCategories?.find((c) => c.code === targetCode);
      if (foundCat) {
        serviceOwnCategoryId = foundCat.id;
      }
    }

    const pricingItemId = line?.pricingItemId ?? line?.service?.pricingItemId ?? null;

    return {
      lineId: line?.id || `line_${idx}`,
      serviceId: line?.service?.medicalServiceId || 0,
      pricingItemId,
      quantity: Math.max(1, toInt(line?.quantity, 1)),
      enteredUnitPrice: toMoney(line?.unitPrice),
      contractPrice: toMoney(line?.contractPrice),
      categoryId: serviceOwnCategoryId,
      serviceCategoryId: serviceOwnCategoryId,
      rejected: !!line?.rejected,
      manualRefusedAmount: toMoney(line?.manualRefusedAmount)
    };
  };

  const fetchCoverage = useCallback(
    async (service, encounterOverride, lineId = null) => {
      const sid = service?.medicalServiceId || 0;
      const pricingItemId = service?.pricingItemId || null;
      let serviceOwnCategoryId =
        service?.categoryId ??
        service?.serviceCategoryId ??
        service?.medicalCategoryId ??
        service?.medicalCategory?.id ??
        service?.effectiveCategory?.id ??
        null;
      const code = service?.serviceCode || service?.code;
      if (code === 'GEN-MEDICATION' || code === 'GEN-MEDICAL-SERVICE') {
        const targetCode = code === 'GEN-MEDICATION' ? 'CAT-DRUG' : 'CAT-DIAGNOSTIC';
        const foundCat = medicalCategories?.find((c) => c.code === targetCode);
        if (foundCat) {
          serviceOwnCategoryId = foundCat.id;
        }
      }
      let categoryId = serviceOwnCategoryId;
      if (!policyId || !member?.id)
        return failedCoverageResult('لا توجد وثيقة أو هوية مستفيد صالحة لحساب التغطية');

      if (!sid && !categoryId && !pricingItemId)
        return failedCoverageResult('الخدمة غير مرتبطة بتصنيف أو عنصر تسعير معتمد');

      try {
        const payload = {
          policyId,
          memberId: member?.id || null,
          serviceYear: serviceYear || null,
          serviceDate: serviceDate || null,
          excludeClaimId: currentClaimId || null,
          fullCoverage,
          encounterType: encounterOverride || encounterType || 'OUTPATIENT',
          lines: [
            {
              lineId: lineId || 'single',
              serviceId: sid,
              pricingItemId,
              quantity: Math.max(1, toInt(service?.quantity, 1)),
              enteredUnitPrice: toMoney(service?.contractPrice),
              contractPrice: toMoney(service?.contractPrice),
              categoryId,
              serviceCategoryId: serviceOwnCategoryId,
              rejected: false,
              manualRefusedAmount: 0
            }
          ]
        };

        const requestId = ++singleRequestIdRef.current;
        diagnosticsRef.current.singleStarted += 1;
        singleAbortRef.current?.abort();
        const controller = new AbortController();
        singleAbortRef.current = controller;
        debugLog('single:start', { requestId });

        const bulkResults = await claimsService.calculateCoverageBulk(payload, {
          signal: controller.signal
        });

        if (requestId !== singleRequestIdRef.current) {
          diagnosticsRef.current.singleStaleIgnored += 1;
          debugLog('single:stale-ignored', { requestId, latest: singleRequestIdRef.current });
          return { __stale: true };
        }

        if (bulkResults && bulkResults.length > 0) {
          return normalizeCoverageResult(bulkResults[0]);
        }
        return failedCoverageResult('لم يُرجع محرك التغطية قراراً لهذه الخدمة');
      } catch (err) {
        const isCanceled =
          err?.name === 'CanceledError' ||
          err?.name === 'AbortError' ||
          err?.originalError?.name === 'CanceledError' ||
          err?.message === 'canceled';

        if (isCanceled) {
          diagnosticsRef.current.singleAborted += 1;
          debugLog('single:aborted', { latest: singleRequestIdRef.current });
          return { __stale: true };
        }
        console.error('[fetchCoverage] error:', err);
        onCoverageError?.('تعذر حساب التغطية. لن تُعتمد حصة الشركة حتى نجاح إعادة الحساب.');
        return failedCoverageResult('تعذر الاتصال بمحرك التغطية — أعد المحاولة');
      }
    },
    [
      policyId,
      member?.id,
      currentClaimId,
      serviceYear,
      serviceDate,
      fullCoverage,
      encounterType,
      onCoverageError
    ]
  );

  const refetchAllLinesCoverage = useCallback(
    async (newEncounterType, currentLines, newFullCoverage) => {
      if (!policyId || !member?.id) {
        return currentLines.map((line, i, all) =>
          recompute(
            line.service
              ? { ...line, ...failedCoverageResult('لا توجد وثيقة أو هوية مستفيد صالحة لحساب التغطية') }
              : line,
            i,
            all
          )
        );
      }

      const effectiveEncounterType = newEncounterType || encounterType || 'OUTPATIENT';
      const isFull = newFullCoverage !== undefined ? newFullCoverage : fullCoverage;

      const linesToCheck = currentLines.filter((l) => l.service);
      if (linesToCheck.length === 0) return currentLines.map((l, i) => recompute(l, i, currentLines));

      const payload = {
        policyId,
        memberId: member.id,
        serviceYear: serviceYear || null,
        serviceDate: serviceDate || null,
        excludeClaimId: currentClaimId || null,
        fullCoverage: isFull,
        encounterType: effectiveEncounterType,
        lines: linesToCheck.map((line, idx) => buildEngineLineInput(line, idx))
      };

      try {
        const requestId = ++bulkRequestIdRef.current;
        diagnosticsRef.current.bulkStarted += 1;
        bulkAbortRef.current?.abort();
        const controller = new AbortController();
        bulkAbortRef.current = controller;
        debugLog('bulk:start', { requestId, lines: linesToCheck.length });

        const bulkResults = await claimsService.calculateCoverageBulk(payload, {
          signal: controller.signal
        });

        if (requestId !== bulkRequestIdRef.current) {
          diagnosticsRef.current.bulkStaleIgnored += 1;
          debugLog('bulk:stale-ignored', { requestId, latest: bulkRequestIdRef.current });
          return null;
        }

        const updated = currentLines.map((line, idx) => {
          if (!line.service) return line;
          const lineId = line.id || `line_${idx}`;
          // Persisted claim-line IDs are numbers in React, while the Java engine
          // contract returns lineId as a string. Compare their canonical form.
          const cov = bulkResults.find((b) => String(b.lineId) === String(lineId));

          if (cov) {
            const normalized = normalizeCoverageResult(cov);
            return {
              ...line,
              ...normalized
            };
          }
          return line;
        });

        return updated.map((line, i) => recompute(line, i, updated));
      } catch (err) {
        const isCanceled =
          err?.name === 'CanceledError' ||
          err?.name === 'AbortError' ||
          err?.originalError?.name === 'CanceledError' ||
          err?.message === 'canceled';

        if (isCanceled) {
          diagnosticsRef.current.bulkAborted += 1;
          debugLog('bulk:aborted', { latest: bulkRequestIdRef.current });
          return null;
        }
        console.error('[refetchAllLinesCoverage] bulk error:', err);
        onCoverageError?.('فشل تحديث تغطية جميع البنود. يرجى المحاولة مرة أخرى.');
        return currentLines.map((line, i, all) =>
          recompute(
            line.service
              ? { ...line, ...failedCoverageResult('فشل تحديث التغطية — أعد المحاولة قبل الحفظ') }
              : line,
            i,
            all
          )
        );
      }
    },
    [
      policyId,
      member?.id,
      encounterType,
      serviceYear,
      serviceDate,
      currentClaimId,
      recompute,
      fullCoverage,
      onCoverageError
    ]
  );

  return {
    fetchCoverage,
    refetchAllLinesCoverage
  };
}
