package com.waad.tba.security;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataAccessService {

    private final RoleService roleService;
    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;
    private final VisitRepository visitRepository;

    public boolean canAccessMember(User user, Long memberId) {
        if (user == null || memberId == null) {
            log.warn("❌ canAccessMember: DENIED - null user or memberId");
            return false;
        }

        if (roleService.isSuperAdmin(user)) {
            log.debug("✅ canAccessMember: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        if (roleService.canAccessInternalOperations(user)) {
            log.debug("✅ canAccessMember: ALLOWED - user={} is internal operations role", user.getUsername());
            return true;
        }

        Optional<Member> memberOpt = memberRepository.findById(memberId);
        if (memberOpt.isEmpty()) {
            log.warn("❌ canAccessMember: DENIED - member {} not found", memberId);
            return false;
        }

        Member member = memberOpt.get();

        if (roleService.isEmployerAdmin(user)) {
            if (user.getEmployerId() == null) {
                log.warn("❌ canAccessMember: DENIED - EMPLOYER_ADMIN user {} has no employerId", user.getUsername());
                return false;
            }
            if (member.getEmployer() == null || !user.getEmployerId().equals(member.getEmployer().getId())) {
                log.warn("❌ canAccessMember: DENIED - user {} attempted to access member {} from different employer",
                        user.getUsername(), memberId);
                return false;
            }
            log.debug("✅ canAccessMember: ALLOWED - user={} employer matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessMember: DENIED - user {} has no valid role for member access", user.getUsername());
        return false;
    }

    public boolean canAccessClaim(User user, Long claimId) {
        if (user == null || claimId == null) {
            log.warn("❌ canAccessClaim: DENIED - null user or claimId");
            return false;
        }

        if (roleService.isSuperAdmin(user)) {
            log.debug("✅ canAccessClaim: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        if (roleService.canAccessInternalOperations(user) || roleService.isFinancialUser(user)) {
            log.debug("✅ canAccessClaim: ALLOWED - user={} is internal/financial role", user.getUsername());
            return true;
        }

        Optional<Claim> claimOpt = claimRepository.findById(claimId);
        if (claimOpt.isEmpty()) {
            log.warn("❌ canAccessClaim: DENIED - claim {} not found", claimId);
            return false;
        }

        Claim claim = claimOpt.get();

        if (roleService.isProvider(user)) {
            if (user.getProviderId() == null) {
                log.warn("❌ canAccessClaim: DENIED - PROVIDER user {} has no providerId", user.getUsername());
                return false;
            }
            if (!user.getProviderId().equals(claim.getProviderId())) {
                log.warn("❌ canAccessClaim: DENIED - user {} attempted to access claim {} from different provider",
                        user.getUsername(), claimId);
                return false;
            }
            log.debug("✅ canAccessClaim: ALLOWED - user={} providerId matches", user.getUsername());
            return true;
        }

        if (roleService.isEmployerAdmin(user)) {
            if (user.getEmployerId() == null) {
                log.warn("❌ canAccessClaim: DENIED - EMPLOYER_ADMIN user {} has no employerId", user.getUsername());
                return false;
            }
            if (claim.getMember() == null || claim.getMember().getEmployer() == null ||
                !user.getEmployerId().equals(claim.getMember().getEmployer().getId())) {
                log.warn("❌ canAccessClaim: DENIED - user {} attempted to access claim {} from different employer",
                        user.getUsername(), claimId);
                return false;
            }
            log.debug("✅ canAccessClaim: ALLOWED - user={} employer matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessClaim: DENIED - user {} has no valid role for claim access", user.getUsername());
        return false;
    }

    public boolean canAccessVisit(User user, Long visitId) {
        if (user == null || visitId == null) {
            log.warn("❌ canAccessVisit: DENIED - null user or visitId");
            return false;
        }

        if (roleService.isSuperAdmin(user)) {
            log.debug("✅ canAccessVisit: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        if (roleService.canAccessInternalOperations(user)) {
            log.debug("✅ canAccessVisit: ALLOWED - user={} is internal operations role", user.getUsername());
            return true;
        }

        Optional<Visit> visitOpt = visitRepository.findById(visitId);
        if (visitOpt.isEmpty()) {
            log.warn("❌ canAccessVisit: DENIED - visit {} not found", visitId);
            return false;
        }

        Visit visit = visitOpt.get();

        if (roleService.isProvider(user)) {
            if (user.getProviderId() == null) {
                log.warn("❌ canAccessVisit: DENIED - PROVIDER user {} has no providerId", user.getUsername());
                return false;
            }
            if (!user.getProviderId().equals(visit.getProviderId())) {
                log.warn("❌ canAccessVisit: DENIED - user {} attempted to access visit {} from different provider",
                        user.getUsername(), visitId);
                return false;
            }
            log.debug("✅ canAccessVisit: ALLOWED - user={} providerId matches", user.getUsername());
            return true;
        }

        if (roleService.isEmployerAdmin(user)) {
            if (user.getEmployerId() == null) {
                log.warn("❌ canAccessVisit: DENIED - EMPLOYER_ADMIN user {} has no employerId", user.getEmployerId());
                return false;
            }
            if (visit.getMember() == null || visit.getMember().getEmployer() == null ||
                !user.getEmployerId().equals(visit.getMember().getEmployer().getId())) {
                log.warn("❌ canAccessVisit: DENIED - user {} attempted to access visit {} from different employer",
                        user.getUsername(), visitId);
                return false;
            }
            log.debug("✅ canAccessVisit: ALLOWED - user={} employer matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessVisit: DENIED - user {} has no valid role for visit access", user.getUsername());
        return false;
    }

    public boolean canAccessProvider(User user, Long providerId) {
        if (user == null || providerId == null) {
            log.warn("❌ canAccessProvider: DENIED - null user or providerId");
            return false;
        }

        if (roleService.isSuperAdmin(user)) {
            log.debug("✅ canAccessProvider: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        if (roleService.canAccessInternalOperations(user)) {
            log.debug("✅ canAccessProvider: ALLOWED - user={} is internal operations role", user.getUsername());
            return true;
        }

        if (roleService.isProvider(user)) {
            if (user.getProviderId() == null) {
                log.warn("❌ canAccessProvider: DENIED - PROVIDER user {} has no providerId", user.getUsername());
                return false;
            }
            if (!user.getProviderId().equals(providerId)) {
                log.warn("❌ canAccessProvider: DENIED - user {} (provider={}) attempted to access provider {}",
                        user.getUsername(), user.getProviderId(), providerId);
                return false;
            }
            log.debug("✅ canAccessProvider: ALLOWED - user={} provider matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessProvider: DENIED - user {} has no valid role for provider access", user.getUsername());
        return false;
    }

    public boolean canModifyClaim(User user, Long claimId) {
        if (user == null || claimId == null) {
            log.warn("❌ canModifyClaim: DENIED - null user or claimId");
            return false;
        }

        if (roleService.isSuperAdmin(user)) {
            log.debug("✅ canModifyClaim: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        if (roleService.canAccessInternalOperations(user)) {
            log.debug("✅ canModifyClaim: ALLOWED - user={} is internal operations role", user.getUsername());
            return true;
        }

        if (roleService.isReviewer(user)) {
            log.debug("✅ canModifyClaim: ALLOWED - user={} is REVIEWER", user.getUsername());
            return true;
        }

        if (roleService.isProvider(user)) {
            Optional<Claim> claimOpt = claimRepository.findById(claimId);
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                if (claim.getProviderId().equals(user.getProviderId())) {
                    if (claim.getStatus().allowsEdit()) {
                        log.debug("✅ canModifyClaim: ALLOWED - user={} is PROVIDER, claim status={} allows edit",
                                user.getUsername(), claim.getStatus());
                        return true;
                    } else {
                        log.warn("❌ canModifyClaim: DENIED - claim status={} does not allow editing",
                                claim.getStatus());
                        return false;
                    }
                } else {
                    log.warn("❌ canModifyClaim: DENIED - provider {} does not own claim {}",
                            user.getProviderId(), claimId);
                    return false;
                }
            }
        }

        if (roleService.isEmployerAdmin(user)) {
            Optional<Claim> claimOpt = claimRepository.findById(claimId);
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                if (claim.getMember() != null &&
                    claim.getMember().getEmployer() != null &&
                    claim.getMember().getEmployer().getId().equals(user.getEmployerId())) {
                    if (claim.getStatus().allowsEdit()) {
                        log.debug("✅ canModifyClaim: ALLOWED - user={} is EMPLOYER_ADMIN, status={} allows edit",
                                user.getUsername(), claim.getStatus());
                        return true;
                    }
                }
            }
        }

        log.warn("❌ canModifyClaim: DENIED - user {} cannot modify claim {}", user.getUsername(), claimId);
        return false;
    }
}
