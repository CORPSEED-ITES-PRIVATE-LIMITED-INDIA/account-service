package com.account.repository;

import com.account.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	// =========================
	// Base user fetch / exists
	// =========================

	@Query("""
        SELECT u
        FROM User u
        WHERE u.email = :email
          AND u.isDeleted = false
    """)
	Optional<User> findByEmail(@Param("email") String email);

	@Query("""
        SELECT u
        FROM User u
        WHERE u.id = :userId
          AND u.isDeleted = false
    """)
	Optional<User> findActiveById(@Param("userId") Long userId);

	@Query("""
        SELECT COUNT(u) > 0
        FROM User u
        WHERE u.id = :id
          AND u.isDeleted = false
    """)
	boolean existsByIdAndNotDeleted(@Param("id") Long id);

	@Query("""
        SELECT u
        FROM User u
        WHERE u.isDeleted = false
    """)
	List<User> findAllActive();


	// =========================
	// RBAC helpers (Role checks)
	// =========================

	/**
	 * Generic role check: does user have a role by name (case-insensitive),
	 * ignoring deleted users and deleted roles.
	 */
	@Query("""
        SELECT COUNT(u) > 0
        FROM User u
        JOIN u.userRole r
        WHERE u.id = :userId
          AND u.isDeleted = false
          AND (r.isDeleted = false OR r.isDeleted IS NULL)
          AND LOWER(r.name) = LOWER(:roleName)
    """)
	boolean hasRole(@Param("userId") Long userId, @Param("roleName") String roleName);

	/**
	 * Admin shortcut.
	 */
	@Query("""
        SELECT COUNT(u) > 0
        FROM User u
        JOIN u.userRole r
        WHERE u.id = :userId
          AND u.isDeleted = false
          AND (r.isDeleted = false OR r.isDeleted IS NULL)
          AND LOWER(r.name) = 'admin'
    """)
	boolean isAdmin(@Param("userId") Long userId);


	// =========================
	// Department checks
	// =========================

	@Query("""
        SELECT COUNT(u) > 0
        FROM User u
        WHERE u.id = :userId
          AND u.isDeleted = false
          AND LOWER(u.department) = 'account'
    """)
	boolean isAccountDepartment(@Param("userId") Long userId);


	// =========================
	// Invoice access policy helper
	// ADMIN OR account department
	// =========================

	@Query("""
        SELECT COUNT(u) > 0
        FROM User u
        LEFT JOIN u.userRole r
        WHERE u.id = :userId
          AND u.isDeleted = false
          AND (
                ( (r.isDeleted = false OR r.isDeleted IS NULL) AND LOWER(r.name) = 'admin' )
                OR LOWER(u.department) = 'account'
          )
    """)
	boolean canAccessInvoices(@Param("userId") Long userId);


	// =========================
	// Optional: search helpers
	// =========================

	@Query("""
        SELECT u
        FROM User u
        WHERE u.isDeleted = false
          AND (
                :q IS NULL
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
              )
    """)
	List<User> searchActiveUsers(@Param("q") String q);
}
