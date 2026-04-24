package com.waad.tba.modules.benefitpolicy.repository;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for BenefitPolicyRule entity.
 * Provides queries for coverage lookup and rule management.
 */
@Repository
public interface BenefitPolicyRuleRepository extends JpaRepository<BenefitPolicyRule, Long> {

  // ═══════════════════════════════════════════════════════════════════════════
  // FIND BY POLICY
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find all rules for a specific policy
   */
  List<BenefitPolicyRule> findByBenefitPolicyId(Long policyId);

  /**
   * Find all rules for a specific policy (paginated)
   */
  Page<BenefitPolicyRule> findByBenefitPolicyId(Long policyId, Pageable pageable);

  /**
   * Find all ACTIVE rules for a specific policy
   */
  List<BenefitPolicyRule> findByBenefitPolicyIdAndDeletedFalseAndActiveTrue(Long policyId);

  /**
   * Backward-compatible alias: active non-deleted rules only
   */
  @Query("SELECT r FROM BenefitPolicyRule r WHERE r.benefitPolicy.id = :policyId AND r.active = true AND r.deleted = false")
  List<BenefitPolicyRule> findByBenefitPolicyIdAndActiveTrue(@Param("policyId") Long policyId);

  /**
   * Count rules for a policy
   */
  long countByBenefitPolicyId(Long policyId);

  /**
   * Count active rules for a policy
   */
  long countByBenefitPolicyIdAndDeletedFalseAndActiveTrue(Long policyId);

  /**
   * Backward-compatible alias: count active non-deleted rules
   */
  @Query("SELECT COUNT(r) FROM BenefitPolicyRule r WHERE r.benefitPolicy.id = :policyId AND r.active = true AND r.deleted = false")
  long countByBenefitPolicyIdAndActiveTrue(@Param("policyId") Long policyId);

  // ═══════════════════════════════════════════════════════════════════════════
  // FIND BY CATEGORY
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find rule for a specific category within a policy
   */
  Optional<BenefitPolicyRule> findByBenefitPolicyIdAndMedicalCategoryId(Long policyId, Long categoryId);

  /**
   * Find active rule for a specific category within a policy
   */
  Optional<BenefitPolicyRule> findByBenefitPolicyIdAndMedicalCategoryIdAndActiveTrue(
      Long policyId, Long categoryId);

  Optional<BenefitPolicyRule> findByBenefitPolicyIdAndMedicalCategoryIdAndDeletedFalseAndActiveTrue(
      Long policyId, Long categoryId);

  /**
   * Find all rules targeting a specific category (across all policies)
   */
  List<BenefitPolicyRule> findByMedicalCategoryId(Long categoryId);

  // ═══════════════════════════════════════════════════════════════════════════
  // COVERAGE LOOKUP QUERIES (Used by Claims/Eligibility)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find the most specific rule for a service within a policy.
   * Service-specific rules take priority over category rules.
   * 
   * Order of precedence:
   * 1. Direct service match
   * 2. Category match (for the service's category)
   */
  @Query("""
      SELECT r FROM BenefitPolicyRule r
      WHERE r.benefitPolicy.id = :policyId
        AND r.active = true
        AND r.deleted = false
        AND r.medicalCategory.id = :categoryId
      ORDER BY r.id
      """)
  List<BenefitPolicyRule> findApplicableRulesForService(
      @Param("policyId") Long policyId,
      @Param("serviceId") Long serviceId,
      @Param("categoryId") Long categoryId);

  /**
   * Find the best matching rule for a service within a policy.
   * Returns the most specific rule (service rule > category rule).
   * Works for both mapped (serviceId given) and unmapped (serviceId=null,
   * categoryId given) lookups.
   */
  @Query("""
      SELECT r FROM BenefitPolicyRule r
      WHERE r.benefitPolicy.id = :policyId
        AND r.active = true
        AND r.deleted = false
        AND (
          (:overrideCategoryId IS NOT NULL AND r.medicalCategory.id = :overrideCategoryId)
          OR (:overrideCategoryId IS NOT NULL AND r.medicalCategory.parentId = :overrideCategoryId)
          OR (:serviceCategoryId IS NOT NULL AND r.medicalCategory.id = :serviceCategoryId)
          OR (:serviceCategoryId IS NOT NULL AND r.medicalCategory.parentId = :serviceCategoryId)
          OR (:parentCategoryId IS NOT NULL AND r.medicalCategory.id = :parentCategoryId)
        )
      ORDER BY
        CASE
          WHEN :overrideCategoryId IS NOT NULL AND r.medicalCategory.parentId = :overrideCategoryId THEN 0
          WHEN :overrideCategoryId IS NOT NULL AND r.medicalCategory.id = :overrideCategoryId THEN 1
          WHEN :serviceCategoryId IS NOT NULL AND r.medicalCategory.id = :serviceCategoryId THEN 2
          WHEN :serviceCategoryId IS NOT NULL AND r.medicalCategory.parentId = :serviceCategoryId THEN 3
          ELSE 4
        END,
        r.id ASC
      LIMIT 1
      """)
  Optional<BenefitPolicyRule> findBestRuleForService(
      @Param("policyId") Long policyId,
      @Param("serviceId") Long serviceId,
      @Param("serviceCategoryId") Long serviceCategoryId,
      @Param("overrideCategoryId") Long overrideCategoryId,
      @Param("parentCategoryId") Long parentCategoryId);

  /**
   * Find active category rule for a policy
   */
  @Query("""
      SELECT r FROM BenefitPolicyRule r
      WHERE r.benefitPolicy.id = :policyId
        AND r.medicalCategory.id = :categoryId
        AND r.active = true
        AND r.deleted = false
      ORDER BY r.id DESC
      """)
  List<BenefitPolicyRule> findActiveCategoryRules(
      @Param("policyId") Long policyId,
      @Param("categoryId") Long categoryId);

  // ═══════════════════════════════════════════════════════════════════════════
  // DUPLICATE CHECK QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Check if a category rule already exists for this policy (excluding given rule
   * id)
   */
  @Query("""
      SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
      FROM BenefitPolicyRule r
      WHERE r.benefitPolicy.id = :policyId
        AND r.medicalCategory.id = :categoryId
        AND r.deleted = false
        AND (:excludeRuleId IS NULL OR r.id != :excludeRuleId)
      """)
  boolean existsCategoryRule(
      @Param("policyId") Long policyId,
      @Param("categoryId") Long categoryId,
      @Param("excludeRuleId") Long excludeRuleId);

  // ═══════════════════════════════════════════════════════════════════════════
  // RULE FILTERING
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find all category-level rules for a policy (no specific service)
   */
  @Query("""
      SELECT r FROM BenefitPolicyRule r
      WHERE r.benefitPolicy.id = :policyId
        AND r.medicalCategory IS NOT NULL
        AND r.active = true
        AND r.deleted = false
      ORDER BY r.medicalCategory.name
      """)
  List<BenefitPolicyRule> findCategoryRulesForPolicy(@Param("policyId") Long policyId);

  /**
   * Find rules that require pre-approval
   */
  List<BenefitPolicyRule> findByBenefitPolicyIdAndDeletedFalseAndRequiresPreApprovalTrue(Long policyId);

  /**
   * Backward-compatible alias: pre-approval rules excluding deleted
   */
  @Query("SELECT r FROM BenefitPolicyRule r WHERE r.benefitPolicy.id = :policyId AND r.requiresPreApproval = true AND r.deleted = false")
  List<BenefitPolicyRule> findByBenefitPolicyIdAndRequiresPreApprovalTrue(@Param("policyId") Long policyId);

  // ═══════════════════════════════════════════════════════════════════════════
  // BATCH OPERATIONS
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Delete all rules for a policy
   */
  void deleteByBenefitPolicyId(Long policyId);

  /**
   * Deactivate all rules for a policy (soft delete)
   */
  @Query("UPDATE BenefitPolicyRule r SET r.active = false WHERE r.benefitPolicy.id = :policyId AND r.deleted = false")
  int deactivateAllForPolicy(@Param("policyId") Long policyId);
}
