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

    public static MemberFilter listing(String status, String type) {
        return new MemberFilter(null, null, null, null, null, null, status, type, DeletedMode.INCLUDE_ALL);
    }

    public static MemberFilter export(String search, Long policyId, String status, String type,
            Boolean includeDeleted) {
        return new MemberFilter(search, null, null, null, null, policyId, status, type,
                Boolean.TRUE.equals(includeDeleted) ? DeletedMode.INCLUDE_ALL : DeletedMode.ACTIVE_ONLY);
    }

    public Specification<Member> toSpecification(AuthorizedMemberScope scope) {
        final Member.MemberStatus parsedStatus = parseStatus(status);
        final TypeCriterion parsedType = parseType(type);
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
            if (parsedType != null) {
                if (parsedType.principal()) predicates.add(cb.isNull(root.get("parent")));
                else {
                    predicates.add(cb.isNotNull(root.get("parent")));
                    if (parsedType.relationship() != null) {
                        predicates.add(cb.equal(root.get("relationship"), parsedType.relationship()));
                    }
                }
            }
            if (deletedMode == DeletedMode.ACTIVE_ONLY) predicates.add(cb.isTrue(root.get("active")));
            if (deletedMode == DeletedMode.DELETED_ONLY) predicates.add(cb.isFalse(root.get("active")));
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
