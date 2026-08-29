package com.waad.tba.modules.member.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.security.AuthorizedMemberScope;
import com.waad.tba.modules.member.security.MemberScopeFilter;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Canonical member filter shared by listing, counting, searching and export. */
public record MemberFilter(
        String nameAr,
        String nameEn,
        String nationalNumber,
        String barcode,
        String cardNumber,
        Long benefitPolicyId,
        String status,
        String type,
        DeletedMode deletedMode) {

    public enum DeletedMode { ACTIVE_ONLY, DELETED_ONLY, INCLUDE_ALL }

    /**
     * The value the status dropdown sends for "members with an exceptional
     * ceiling uplift in force".
     *
     * It arrives through the status control because that is where a user looks
     * for it, and it is NOT a status: a member with an uplift is also ACTIVE,
     * or SUSPENDED, or anything else. Folding it into the status column would
     * repeat exactly the mistake the comment further down describes -- two
     * different questions sharing one field, so answering one silently
     * answers the other. It is recognised here, kept out of parseStatus, and
     * applied as its own predicate.
     */
    public static final String WITH_UPLIFT = "WITH_UPLIFT";

    public static MemberFilter listing(String status, String type) {
        return new MemberFilter(null, null, null, null, null, null, status, type, DeletedMode.INCLUDE_ALL);
    }

    public static MemberFilter export(String search, Long policyId, String status, String type,
            Boolean includeDeleted) {
        return new MemberFilter(search, null, null, null, null, policyId, status, type,
                Boolean.TRUE.equals(includeDeleted) ? DeletedMode.INCLUDE_ALL : DeletedMode.ACTIVE_ONLY);
    }

    public Specification<Member> toSpecification(AuthorizedMemberScope scope) {
        final boolean upliftedOnly = WITH_UPLIFT.equalsIgnoreCase(trim(status));
        final Member.MemberStatus parsedStatus = upliftedOnly ? null : parseStatus(status);
        final TypeCriterion parsedType = parseType(type);
        final java.time.LocalDate today = java.time.LocalDate.now();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(MemberScopeFilter.toPredicate(scope, root.get("employer").get("id"), cb));

            addTextSearch(predicates, root, cb);
            if (benefitPolicyId != null) {
                predicates.add(cb.equal(root.get("benefitPolicy").get("id"), benefitPolicyId));
            }
            if (parsedStatus != null) {
                predicates.add(cb.equal(root.get("status"), parsedStatus));
            }
            if (upliftedOnly) {
                // EXISTS rather than a join: a member can hold more than one
                // uplift, and a join would return them once per uplift.
                var uplifts = query.subquery(Long.class);
                var uplift = uplifts.from(
                        com.waad.tba.modules.member.entity.MemberGeneralLimitUplift.class);
                uplifts.select(cb.literal(1L)).where(
                        cb.equal(uplift.get("memberId"), root.get("id")),
                        cb.lessThanOrEqualTo(uplift.get("effectiveFrom"), today),
                        cb.or(cb.isNull(uplift.get("effectiveTo")),
                                cb.greaterThan(uplift.get("effectiveTo"), today)));
                predicates.add(cb.exists(uplifts));
            }
            if (parsedType != null) {
                if (parsedType.principal()) predicates.add(cb.isNull(root.get("parent")));
                else {
                    predicates.add(cb.isNotNull(root.get("parent")));
                    if (parsedType.relationship() != null) {
                        predicates.add(cb.equal(root.get("relationship"), parsedType.relationship()));
                    }
                }
            }
            // An explicit status is a complete answer on its own: the caller asked
            // for SUSPENDED, not for "SUSPENDED and also active=true". Layering the
            // active/inactive gate on top of it would make an explicit status filter
            // silently return nothing whenever it names a status the gate excludes
            // (e.g. SUSPENDED under the default ACTIVE_ONLY search). The gate still
            // applies to the undifferentiated "no status chosen" case, where it is
            // the only thing distinguishing an active roster from a terminated one.
            if (parsedStatus == null) {
                if (deletedMode == DeletedMode.ACTIVE_ONLY) predicates.add(cb.isTrue(root.get("active")));
                if (deletedMode == DeletedMode.DELETED_ONLY) predicates.add(cb.isFalse(root.get("active")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addTextSearch(List<Predicate> predicates, jakarta.persistence.criteria.Root<Member> root,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        List<Predicate> names = new ArrayList<>();
        addGeneralTerm(names, root, cb, nameAr);
        if (hasText(nameEn) && !nameEn.trim().equalsIgnoreCase(trim(nameAr))) addGeneralTerm(names, root, cb, nameEn);
        if (!names.isEmpty()) predicates.add(cb.or(names.toArray(Predicate[]::new)));
        if (hasText(nationalNumber)) predicates.add(cb.like(cb.lower(root.get("nationalNumber")), like(nationalNumber)));
        if (hasText(barcode)) predicates.add(cb.like(cb.lower(root.get("barcode")), like(barcode)));
        if (hasText(cardNumber)) predicates.add(cb.like(cb.lower(root.get("cardNumber")), like(cardNumber)));
    }

    private static void addGeneralTerm(List<Predicate> out, jakarta.persistence.criteria.Root<Member> root,
            jakarta.persistence.criteria.CriteriaBuilder cb, String value) {
        if (!hasText(value)) return;
        String pattern = like(value);
        out.add(cb.like(cb.lower(root.get("fullName")), pattern));
        out.add(cb.like(cb.lower(root.get("cardNumber")), pattern));
        out.add(cb.like(cb.lower(root.get("nationalNumber")), pattern));
        out.add(cb.like(cb.lower(root.get("barcode")), pattern));
    }

    private static Member.MemberStatus parseStatus(String value) {
        if (!hasText(value)) return null;
        try { return Member.MemberStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new BusinessRuleException("حالة المستفيد غير معروفة: " + value); }
    }

    private static TypeCriterion parseType(String value) {
        if (!hasText(value)) return null;
        if ("PRINCIPAL".equalsIgnoreCase(value)) return new TypeCriterion(true, null);
        if ("DEPENDENT".equalsIgnoreCase(value)) return new TypeCriterion(false, null);
        try { return new TypeCriterion(false, Member.Relationship.valueOf(value.trim().toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ex) { throw new BusinessRuleException("نوع المستفيد غير معروف: " + value); }
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String like(String value) { return "%" + value.trim().toLowerCase(Locale.ROOT) + "%"; }
    private record TypeCriterion(boolean principal, Member.Relationship relationship) {}
}
