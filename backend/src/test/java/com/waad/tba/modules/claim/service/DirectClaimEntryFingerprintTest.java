package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.api.request.DirectClaimEntryRequest;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fingerprint decides whether a retry is the same claim or a different one,
 * and both ways of being wrong are expensive.
 *
 * <p>Too strict and a genuine retry -- the same command sent again after a
 * timeout -- is refused as "a different claim", so the operator who sees no
 * claim enters it by hand and the account is billed twice. Too loose and a
 * second, genuinely different claim is swallowed as a replay and never recorded.
 *
 * <p>These tests pin both edges: what must not change the answer, and what must.
 */
class DirectClaimEntryFingerprintTest {

    private final DirectClaimEntryFingerprint fingerprint = new DirectClaimEntryFingerprint();

    // ── what must NOT change the answer ──────────────────────────────────────

    @Test
    void isBlindToTheOrderTheLinesArriveIn() {
        var first = request(line(10L, 2, "50.00"), line(20L, 1, "30.00"));
        var second = request(line(20L, 1, "30.00"), line(10L, 2, "50.00"));

        assertThat(fingerprint.of(first))
                .as("the same two lines listed the other way round are the same claim")
                .isEqualTo(fingerprint.of(second));
    }

    @Test
    void isBlindToTrailingWhitespaceAndCaseInText() {
        var plain = request(line(10L, 1, "50.00"));
        plain.getClaim().setDoctorName("د. سالم");
        plain.getClaim().setDiagnosisDescription("التهاب حاد");

        var padded = request(line(10L, 1, "50.00"));
        padded.getClaim().setDoctorName("  د. سالم  ");
        padded.getClaim().setDiagnosisDescription("التهاب   حاد");

        assertThat(fingerprint.of(plain)).isEqualTo(fingerprint.of(padded));
    }

    @Test
    void isBlindToTrailingZeroesInPrices() {
        assertThat(fingerprint.of(request(line(10L, 1, "50"))))
                .as("50 and 50.00 are the same money, not two commands")
                .isEqualTo(fingerprint.of(request(line(10L, 1, "50.00"))));
    }

    @Test
    void isBlindToFieldsThatDoNotChangeWhatIsClaimed() {
        var plain = request(line(10L, 1, "50.00"));
        var annotated = request(line(10L, 1, "50.00"));
        annotated.getClaim().setNotes("أعيد الإرسال بعد انقطاع");
        annotated.setIdempotencyKey("a-different-key");

        assertThat(fingerprint.of(plain)).isEqualTo(fingerprint.of(annotated));
    }

    // ── what MUST change the answer ──────────────────────────────────────────

    @Test
    void changesWhenALineIsAdded() {
        assertThat(fingerprint.of(request(line(10L, 1, "50.00"))))
                .isNotEqualTo(fingerprint.of(request(line(10L, 1, "50.00"), line(11L, 1, "20.00"))));
    }

    @Test
    void changesWhenAQuantityOrPriceChanges() {
        String base = fingerprint.of(request(line(10L, 1, "50.00")));

        assertThat(fingerprint.of(request(line(10L, 2, "50.00")))).isNotEqualTo(base);
        assertThat(fingerprint.of(request(line(10L, 1, "60.00")))).isNotEqualTo(base);
    }

    @Test
    void changesWhenTheMemberOrDateChanges() {
        String base = fingerprint.of(request(line(10L, 1, "50.00")));

        var otherMember = request(line(10L, 1, "50.00"));
        otherMember.getClaim().setMemberId(999L);
        assertThat(fingerprint.of(otherMember)).isNotEqualTo(base);

        var otherDate = request(line(10L, 1, "50.00"));
        otherDate.getClaim().setServiceDate(LocalDate.of(2026, 9, 2));
        assertThat(fingerprint.of(otherDate)).isNotEqualTo(base);
    }

    /**
     * Adjacent fields must not be able to borrow each other's characters. With
     * no separator between them, member 12 + provider 3 and member 1 + provider
     * 23 produce the same text, and two unrelated claims become a replay of one
     * another.
     */
    @Test
    void keepsAdjacentFieldsFromRunningIntoEachOther() {
        var first = request(line(10L, 1, "50.00"));
        first.getClaim().setMemberId(12L);
        first.getClaim().setProviderId(3L);

        var second = request(line(10L, 1, "50.00"));
        second.getClaim().setMemberId(1L);
        second.getClaim().setProviderId(23L);

        assertThat(fingerprint.of(first)).isNotEqualTo(fingerprint.of(second));
    }

    @Test
    void changesWhenTheClaimContextChanges() {
        var outpatient = request(line(10L, 1, "50.00"));
        var maternity = request(line(10L, 1, "50.00"));
        maternity.getClaim().setClaimContextCode("MATERNITY");

        assertThat(fingerprint.of(outpatient)).isNotEqualTo(fingerprint.of(maternity));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private CreateClaimRequest.ClaimLineRequest line(Long pricingItemId, int quantity, String unitPrice) {
        var line = new CreateClaimRequest.ClaimLineRequest();
        line.setPricingItemId(pricingItemId);
        line.setQuantity(quantity);
        line.setUnitPrice(new BigDecimal(unitPrice));
        return line;
    }

    private DirectClaimEntryRequest request(CreateClaimRequest.ClaimLineRequest... lines) {
        var claim = new CreateClaimRequest();
        claim.setMemberId(7L);
        claim.setProviderId(3L);
        claim.setServiceDate(LocalDate.of(2026, 9, 1));
        claim.setDoctorName("د. سالم");
        claim.setEncounterType(EncounterType.OUTPATIENT);
        claim.setClaimContextCode("OUTPATIENT");
        claim.setLines(List.of(lines));

        var request = new DirectClaimEntryRequest();
        request.setIdempotencyKey("key-1");
        request.setEmployerId(5L);
        request.setClaim(claim);
        return request;
    }
}
