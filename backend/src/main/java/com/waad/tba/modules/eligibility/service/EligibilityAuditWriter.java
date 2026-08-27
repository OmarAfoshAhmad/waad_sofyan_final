package com.waad.tba.modules.eligibility.service;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.entity.EligibilityCheck;
import com.waad.tba.modules.eligibility.repository.EligibilityCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The actual write, isolated in its own bean so its REQUIRES_NEW transaction
 * boundary is real (not defeated by self-invocation) and so a flush failure
 * here can be caught by EligibilityAuditRecorder from OUTSIDE this
 * transaction. Catching a flush failure from inside the same
 * @Transactional(REQUIRES_NEW) method that produced it does not work: once
 * Hibernate's flush fails, Spring/Hibernate mark that transaction
 * rollback-only regardless of whether the exception is caught in application
 * code, so a method that swallows the exception and returns normally still
 * gets an UnexpectedRollbackException from the transaction interceptor at
 * commit. Letting the exception propagate out of this class -- to be caught
 * by EligibilityAuditRecorder, a separate transactional scope entirely --
 * is the only way to both roll back cleanly here AND report the failure as a
 * normal return value one level up.
 */
@Service
@RequiredArgsConstructor
class EligibilityAuditWriter {

    private final EligibilityCheckRepository eligibilityCheckRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(EligibilityContext context, EligibilityResult result) {
        EligibilityCheck check = EligibilityCheck.builder()
                .requestId(context.getRequestId())
                .checkTimestamp(context.getCheckTimestamp())
                // Input
                .memberId(context.getMemberId())
                .policyId(context.getBenefitPolicyId())
                .providerId(context.getProviderId())
                .serviceDate(context.getServiceDate())
                .serviceCode(context.getServiceCode())
                // Result
                .eligible(result.isEligible())
                .status(result.getStatus().name())
                .reasons(convertReasonsToJson(result.getReasons()))
                // Snapshot
                .memberName(result.getSnapshot() != null ? result.getSnapshot().getMemberName() : null)
                .memberCivilId(result.getSnapshot() != null ? result.getSnapshot().getMemberCivilId() : null)
                .memberStatus(result.getSnapshot() != null ? result.getSnapshot().getMemberStatus() : null)
                .policyNumber(result.getSnapshot() != null ? result.getSnapshot().getPolicyNumber() : null)
                .policyStatus(result.getSnapshot() != null ? result.getSnapshot().getPolicyStatus() : null)
                .policyStartDate(result.getSnapshot() != null ? result.getSnapshot().getCoverageStart() : null)
                .policyEndDate(result.getSnapshot() != null ? result.getSnapshot().getCoverageEnd() : null)
                .employerId(result.getSnapshot() != null ? result.getSnapshot().getEmployerId() : null)
                .employerName(result.getSnapshot() != null ? result.getSnapshot().getEmployerName() : null)
                // Security
                .checkedByUserId(context.getCheckedByUserId())
                .checkedByUsername(context.getCheckedByUsername())
                .companyScopeId(context.getCompanyScopeId())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                // Metrics
                .processingTimeMs((int) result.getProcessingTimeMs())
                .rulesEvaluated(result.getRulesEvaluated())
                .build();

        // Explicit flush: forces the INSERT to run (and fail, if it's going
        // to) now, inside this transaction, instead of being deferred to a
        // commit that happens after the caller has already decided what to do.
        eligibilityCheckRepository.saveAndFlush(check);
    }

    private String convertReasonsToJson(List<EligibilityResult.ReasonDetail> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < reasons.size(); i++) {
            EligibilityResult.ReasonDetail r = reasons.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"code\":\"").append(escape(r.getCode())).append("\",");
            sb.append("\"messageAr\":\"").append(escape(r.getMessageAr())).append("\",");
            sb.append("\"details\":\"").append(escape(r.getDetails())).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
