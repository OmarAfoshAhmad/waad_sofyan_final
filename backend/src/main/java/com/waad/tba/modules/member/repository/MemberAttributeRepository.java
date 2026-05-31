package com.waad.tba.modules.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.member.entity.MemberAttribute;
import com.waad.tba.modules.member.entity.MemberAttribute.AttributeSource;

@Repository
public interface MemberAttributeRepository extends JpaRepository<MemberAttribute, Long> {
    
    /**
     * Find all attributes for a member
     */
    List<MemberAttribute> findByMemberId(Long memberId);
    
    /**
     * Find specific attribute for a member
     */
    Optional<MemberAttribute> findByMemberIdAndAttributeCode(Long memberId, String attributeCode);
    
    /**
     * Find attributes by code across all members
     */
    List<MemberAttribute> findByAttributeCode(String attributeCode);
    
    /**
     * Find attributes by source
     */
    List<MemberAttribute> findBySource(AttributeSource source);
    
    /**
     * Find members with specific attribute value
     */
    @Query("SELECT ma FROM MemberAttribute ma WHERE ma.attributeCode = :code AND ma.attributeValue = :value")
    List<MemberAttribute> findByAttributeCodeAndValue(
            @Param("code") String attributeCode, 
            @Param("value") String attributeValue);
    
    /**
     * Search attributes by value (partial match)
     */
    @Query("SELECT ma FROM MemberAttribute ma WHERE ma.attributeCode = :code " +
           "AND LOWER(ma.attributeValue) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<MemberAttribute> searchByAttributeValue(
            @Param("code") String attributeCode, 
            @Param("search") String search);
    
    /**
     * Delete all attributes for a member
     */
    @Modifying
    @Query("DELETE FROM MemberAttribute ma WHERE ma.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
    
    /**
     * Delete specific attribute
     */
    @Modifying
    @Query("DELETE FROM MemberAttribute ma WHERE ma.member.id = :memberId AND ma.attributeCode = :code")
    void deleteByMemberIdAndAttributeCode(
            @Param("memberId") Long memberId, 
            @Param("code") String attributeCode);
    
    /**
     * Check if member has a specific attribute
     */
    boolean existsByMemberIdAndAttributeCode(Long memberId, String attributeCode);
    
    /**
     * Count attributes for a member
     */
    long countByMemberId(Long memberId);
    
    /**
     * Get distinct attribute codes used
     */
    @Query("SELECT DISTINCT ma.attributeCode FROM MemberAttribute ma ORDER BY ma.attributeCode")
    List<String> findDistinctAttributeCodes();
    
    /**
     * Get distinct values for a specific attribute code
     */
    @Query("SELECT DISTINCT ma.attributeValue FROM MemberAttribute ma " +
           "WHERE ma.attributeCode = :code AND ma.attributeValue IS NOT NULL " +
           "ORDER BY ma.attributeValue")
    List<String> findDistinctValuesForAttribute(@Param("code") String attributeCode);

    /**
     * PHASE 5.B: Batch query attributes for multiple member IDs to avoid N+1
     * @param memberIds Collection of member IDs
     * @return List of all attributes for given member IDs
     */
    @Query("SELECT ma FROM MemberAttribute ma WHERE ma.member.id IN :memberIds")
    List<MemberAttribute> findByMemberIdIn(@Param("memberIds") List<Long> memberIds);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM MemberAttribute ma WHERE ma.member.id IN :memberIds")
    void deleteByMemberIdIn(@Param("memberIds") java.util.Collection<Long> memberIds);
}
