package com.waad.tba.modules.rbac.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.rbac.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    Boolean existsByUsernameIgnoreCase(String username);
    Boolean existsByEmailIgnoreCase(String email);

    /**
     * Count active SUPER_ADMIN accounts — used to block demoting/deactivating
     * the last one, which would lock out all administrative access with no
     * recovery path.
     */
    long countByUserTypeAndActiveTrue(String userType);
    
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchUsers(String query);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<User> searchUsers(String query, Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
           "(:role IS NULL OR :role = '' OR u.userType = :role) AND (" +
           ":query IS NULL OR :query = '' OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> searchUsersFiltered(@Param("query") String query, @Param("role") String role, Pageable pageable);
    
    Optional<User> findByUsernameOrEmail(String username, String email);
    
    /**
     * Find users not assigned to any provider (providerId is null)
     * Used in provider management to show available users for linking
     */
    List<User> findByProviderIdIsNull();
    
    /**
     * Find users assigned to a specific provider
     * Used in provider management to show linked account manager
     */
    List<User> findByProviderId(Long providerId);
}
