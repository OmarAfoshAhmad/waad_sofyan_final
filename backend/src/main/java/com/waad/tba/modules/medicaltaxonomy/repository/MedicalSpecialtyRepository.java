package com.waad.tba.modules.medicaltaxonomy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalSpecialty;

@Repository
public interface MedicalSpecialtyRepository extends JpaRepository<MedicalSpecialty, Long> {

  /** Active (non-deleted) list — used by dropdowns. */
  List<MedicalSpecialty> findAllByDeletedFalseOrderByNameAr();

  /** Active specialties belonging to a specific category. */
  List<MedicalSpecialty> findAllByCategoryIdAndDeletedFalseOrderByNameAr(Long categoryId);

  Optional<MedicalSpecialty> findByCode(String code);

  boolean existsByCode(String code);

  /** Count how many active (non-deleted) services reference this specialty. */
  @Query("""
      SELECT COUNT(s) FROM MedicalService s
      WHERE  s.specialtyId = :specialtyId
        AND  s.deleted      = false
      """)
  long countActiveServicesBySpecialty(@Param("specialtyId") Long specialtyId);

  /** Check if any non-deleted specialty belongs to a category. */
  boolean existsByCategoryIdAndDeletedFalse(Long categoryId);
}
