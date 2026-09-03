package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.claim.api.ClaimApiMapper;
import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.api.request.DirectClaimEntryRequest;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.entity.MemberEmployerAssignment;
import com.waad.tba.modules.member.entity.MemberPolicyAssignment;
import com.waad.tba.modules.member.service.MemberContextResolver;
import com.waad.tba.modules.member.service.MemberDatedContext;
import com.waad.tba.modules.visit.dto.VisitResponseDto;
import com.waad.tba.modules.visit.service.VisitService;
import com.waad.tba.support.PostgresIntegrationTestBase;

/** Proves the outer direct-entry transaction rolls a written visit back. */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DirectClaimEntryRollbackIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired DirectClaimEntryService service;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean VisitService visitService;
    @MockitoBean ClaimService claimService;
    @MockitoBean ClaimApiMapper claimApiMapper;
    @MockitoBean MemberContextResolver memberContextResolver;

    @Test
    void aFailureAfterTheVisitInsertLeavesNeitherHalfOfTheCommand() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long employerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "ROLL-" + suffix, "Rollback employer " + suffix);
        long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                        + "default_coverage_percent, start_date, end_date, status, active) "
                        + "VALUES (?, ?, ?, 1000, 100, CURRENT_DATE - 1, CURRENT_DATE + 1, 'ACTIVE', true) RETURNING id",
                Long.class, "Rollback policy " + suffix, "RP-" + suffix, employerId);
        long memberId = jdbc.queryForObject(
                "INSERT INTO members (employer_id, benefit_policy_id, full_name, card_number, barcode, status, active) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, employerId, policyId, "Rollback member", "RC-" + suffix, "RC-" + suffix);
        long providerId = jdbc.queryForObject(
                "INSERT INTO providers (name, license_number, provider_type) VALUES (?, ?, 'CLINIC') RETURNING id",
                Long.class, "Rollback provider", "RL-" + suffix);

        LocalDate date = LocalDate.now();
        var context = new MemberDatedContext(memberId, date,
                MemberEmployerAssignment.builder().id(1L).build(), Employer.builder().id(employerId).build(),
                MemberPolicyAssignment.builder().id(2L).build(), BenefitPolicy.builder().id(policyId).build());
        when(memberContextResolver.resolveForOrFail(memberId, date)).thenReturn(context);

        ClaimCreateDto mapped = ClaimCreateDto.builder()
                .memberId(memberId).providerId(providerId).serviceDate(date)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(1L).quantity(1).build()))
                .build();
        CreateClaimRequest claim = CreateClaimRequest.builder()
                .memberId(memberId).providerId(providerId).serviceDate(date).doctorName("طبيب الاختبار")
                .lines(List.of(CreateClaimRequest.ClaimLineRequest.builder()
                        .medicalServiceId(1L).quantity(1).build()))
                .build();
        DirectClaimEntryRequest request = DirectClaimEntryRequest.builder()
                .idempotencyKey("rollback-" + suffix).employerId(employerId).claim(claim).build();

        when(claimApiMapper.toCreateDto(claim)).thenReturn(mapped);
        when(visitService.create(any())).thenAnswer(invocation -> {
            Long visitId = jdbc.queryForObject(
                    "INSERT INTO visits (member_id, provider_id, visit_date, doctor_name, status) "
                            + "VALUES (?, ?, ?, ?, 'REGISTERED') RETURNING id",
                    Long.class, memberId, providerId, date, "طبيب الاختبار");
            return VisitResponseDto.builder().id(visitId).build();
        });
        when(claimService.createClaim(eq(mapped), any())).thenThrow(new IllegalStateException("late claim failure"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("late claim failure");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM visits WHERE member_id = ? AND provider_id = ?",
                Long.class, memberId, providerId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM claims WHERE member_id = ? AND provider_id = ?",
                Long.class, memberId, providerId)).isZero();
    }

    @Test
    void concurrentRetriesCreateOneVisitAndOneClaimAndReturnTheSameClaim() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long employerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "IDEM-" + suffix, "Idempotent employer " + suffix);
        long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                        + "default_coverage_percent, start_date, end_date, status, active) "
                        + "VALUES (?, ?, ?, 1000, 100, CURRENT_DATE - 1, CURRENT_DATE + 1, 'ACTIVE', true) RETURNING id",
                Long.class, "Idempotent policy " + suffix, "IP-" + suffix, employerId);
        long memberId = jdbc.queryForObject(
                "INSERT INTO members (employer_id, benefit_policy_id, full_name, card_number, barcode, status, active) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, employerId, policyId, "Idempotent member", "IC-" + suffix, "IC-" + suffix);
        long providerId = jdbc.queryForObject(
                "INSERT INTO providers (name, license_number, provider_type) VALUES (?, ?, 'CLINIC') RETURNING id",
                Long.class, "Idempotent provider", "IL-" + suffix);
        LocalDate date = LocalDate.now();
        var context = new MemberDatedContext(memberId, date,
                MemberEmployerAssignment.builder().id(11L).build(), Employer.builder().id(employerId).build(),
                MemberPolicyAssignment.builder().id(12L).build(), BenefitPolicy.builder().id(policyId).build());
        when(memberContextResolver.resolveForOrFail(memberId, date)).thenReturn(context);

        CreateClaimRequest claim = CreateClaimRequest.builder()
                .memberId(memberId).providerId(providerId).serviceDate(date).doctorName("طبيب التزامن")
                .lines(List.of(CreateClaimRequest.ClaimLineRequest.builder()
                        .medicalServiceId(1L).quantity(1).build()))
                .build();
        DirectClaimEntryRequest request = DirectClaimEntryRequest.builder()
                .idempotencyKey("concurrent-" + suffix).employerId(employerId).claim(claim).build();
        when(claimApiMapper.toCreateDto(claim)).thenAnswer(ignored -> ClaimCreateDto.builder()
                .memberId(memberId).providerId(providerId).serviceDate(date)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(1L).quantity(1).build()))
                .build());

        AtomicInteger visitCreates = new AtomicInteger();
        AtomicInteger claimCreates = new AtomicInteger();
        when(visitService.create(any())).thenAnswer(invocation -> {
            visitCreates.incrementAndGet();
            Long id = jdbc.queryForObject("INSERT INTO visits (member_id, provider_id, visit_date, doctor_name, status) "
                    + "VALUES (?, ?, ?, ?, 'REGISTERED') RETURNING id", Long.class,
                    memberId, providerId, date, "طبيب التزامن");
            return VisitResponseDto.builder().id(id).build();
        });
        when(claimService.createClaim(any(), any())).thenAnswer(invocation -> {
            claimCreates.incrementAndGet();
            ClaimCreateDto dto = invocation.getArgument(0);
            Long id = jdbc.queryForObject("INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                    + "service_date, requested_amount, patient_copay, status, claim_context_code) "
                    + "VALUES (?, ?, ?, ?, ?, 100, 100, 'DRAFT', 'OUTPATIENT') RETURNING id",
                    Long.class, "IDEM-CLM-" + suffix, memberId, providerId, dto.getVisitId(), date);
            return ClaimViewDto.builder().id(id).build();
        });

        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return service.create(request).getId(); });
            var second = pool.submit(() -> { start.await(); return service.create(request).getId(); });
            start.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(visitCreates).hasValue(1);
        assertThat(claimCreates).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits WHERE member_id=? AND provider_id=?",
                Long.class, memberId, providerId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claims WHERE member_id=? AND provider_id=?",
                Long.class, memberId, providerId)).isEqualTo(1L);
    }
}
