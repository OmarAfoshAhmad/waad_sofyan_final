package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.api.request.DirectClaimEntryRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * The fingerprint that decides whether a retry is the same command.
 *
 * <p>It used to be a SHA-256 of the request's JSON. That made the answer depend
 * on how Jackson happened to serialise the object: a reordered field, a new
 * optional property, a nested null written where it was previously omitted --
 * any of these produce a different hash for an identical command. The failure is
 * not academic. A client that retries after a timeout is told
 * "مفتاح إعادة الإرسال مستخدم لبيانات مطالبة مختلفة" and the operator, seeing no
 * claim, enters it again by hand -- so the safeguard against double entry
 * becomes the thing that causes it.
 *
 * <p>What is hashed here is an explicit list of the fields that actually decide
 * whether two commands are the same claim, written in a fixed order, with the
 * lines sorted by a stable key so the client's array order cannot change the
 * answer. Amounts are normalised to two decimals because 100 and 100.00 are the
 * same money.
 *
 * <p>Fields deliberately excluded: anything that does not change what is being
 * claimed -- notes, the idempotency key itself, presentation-only values. A
 * retry that differs only in those is still the same command.
 */
@Component
public class DirectClaimEntryFingerprint {

    /**
     * ASCII unit and record separators. Real separators, not empty strings:
     * without them "12" + "3" and "1" + "23" hash identically, and two different
     * claims would be read as a retry of one another. Neither character can occur
     * in a name, a code or a number.
     */
    private static final String FIELD = String.valueOf((char) 0x1F);
    private static final String RECORD = String.valueOf((char) 0x1E);

    public String of(DirectClaimEntryRequest request) {
        CreateClaimRequest claim = request.getClaim();
        StringBuilder canonical = new StringBuilder("v1")
                .append(FIELD).append(text(request.getEmployerId()))
                .append(FIELD).append(text(claim == null ? null : claim.getMemberId()))
                .append(FIELD).append(text(claim == null ? null : claim.getProviderId()))
                .append(FIELD).append(text(claim == null ? null : claim.getServiceDate()))
                .append(FIELD).append(text(claim == null ? null : claim.getClaimBatchId()))
                .append(FIELD).append(normalized(claim == null ? null : claim.getDiagnosisCode()))
                .append(FIELD).append(normalized(claim == null ? null : claim.getDiagnosisDescription()))
                .append(FIELD).append(normalized(claim == null ? null : claim.getDoctorName()))
                .append(FIELD).append(normalized(claim == null ? null : claim.getClaimContextCode()))
                .append(FIELD).append(text(claim == null ? null : claim.getEncounterType()))
                .append(FIELD).append(text(claim == null ? null : claim.getFullCoverage()))
                .append(FIELD).append(text(claim == null ? null : claim.getPreAuthorizationId()));

        for (String line : canonicalLines(claim)) {
            canonical.append(RECORD).append(line);
        }
        return sha256(canonical.toString());
    }

    /**
     * One string per line, sorted. The sort key is the line's identity -- the
     * priced item, the service, the category -- so that two requests listing the
     * same lines in a different order agree, while a request that genuinely adds
     * or changes a line does not.
     */
    private List<String> canonicalLines(CreateClaimRequest claim) {
        if (claim == null || claim.getLines() == null) {
            return List.of();
        }
        return claim.getLines().stream()
                .filter(java.util.Objects::nonNull)
                .map(line -> String.join(FIELD,
                        text(line.getPricingItemId()),
                        text(line.getMedicalServiceId()),
                        text(line.getPendingServiceId()),
                        text(line.getServiceCategoryId()),
                        text(line.getQuantity()),
                        money(line.getUnitPrice()),
                        text(line.getRejected())))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** Two decimals, so 100 and 100.00 are one price and not two commands. */
    private String money(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Surrounding and repeated whitespace is not part of what is being claimed,
     * and case is not either: a doctor's name retyped in a different case is the
     * same doctor.
     */
    private String normalized(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("تعذر بناء بصمة أمر المطالبة", ex);
        }
    }
}
