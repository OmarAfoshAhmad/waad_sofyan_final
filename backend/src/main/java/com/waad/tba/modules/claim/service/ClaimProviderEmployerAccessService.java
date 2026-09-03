package com.waad.tba.modules.claim.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * The two employer questions a claim has to answer, each asked in one place.
 *
 * <p>They are different questions and were being confused for one another. The
 * first asks whether the member belonged to the batch's employer <em>on the
 * service date</em> -- a temporal fact about the member. The second asks whether
 * the provider is contracted to serve that employer at all -- a network fact
 * about the provider. Claim entry checked the first twice with two different
 * messages, and the second lived inline in the middle of claim creation.
 *
 * <p>Gathering them here is not an abstraction for its own sake: it means a
 * change to either rule -- who counts as in-network, what an archived employer
 * may still accept -- lands in one place rather than being applied to two of the
 * three callers and forgotten in the third.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimProviderEmployerAccessService {

    private final ProviderAllowedEmployerRepository providerAllowedEmployerRepository;

    /**
     * The member must belong to the employer the batch is being entered under,
     * as of the service date -- not as of today. A member who moved employers
     * last month still bills last month's claims to last month's employer.
     */
    public void requireMemberBelongsToEmployer(Long requestedEmployerId,
                                               Employer employerOnServiceDate,
                                               LocalDate serviceDate) {
        if (employerOnServiceDate == null
                || requestedEmployerId == null
                || !requestedEmployerId.equals(employerOnServiceDate.getId())) {
            throw new BusinessRuleException(
                    "المستفيد لا يتبع جهة عمل الدفعة في تاريخ الخدمة " + serviceDate);
        }
    }

    /**
     * The provider must be able to serve that employer: either it is a global
     * network provider, or it holds an active link to this employer.
     *
     * <p>Answers 403 rather than a business error because this is a tenant
     * boundary, not a data-entry mistake: a provider reaching an employer it has
     * no relationship with is an attempt to cross into another tenant's data.
     */
    public void requireProviderServesEmployer(Provider provider, Employer employer, Long memberId) {
        if (provider == null || employer == null) {
            return;
        }
        boolean isGlobalProvider = Boolean.TRUE.equals(provider.getAllowAllEmployers());
        boolean authorized = isGlobalProvider
                || providerAllowedEmployerRepository.hasActiveAccessToEmployer(
                        provider.getId(), employer.getId());
        if (authorized) {
            return;
        }
        log.error("SECURITY: provider {} attempted a claim for unauthorized employer {} (member {})",
                provider.getId(), employer.getId(), memberId);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "المزود غير مخول لتقديم خدمات لموظفي هذه الجهة (" + employer.getName() + ").");
    }
}
