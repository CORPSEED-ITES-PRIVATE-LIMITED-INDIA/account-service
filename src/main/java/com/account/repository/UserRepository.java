package com.account.repository;

import com.account.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	// Find active (non-deleted) user by email
	Optional<User> findByEmailAndIsDeletedFalse(String email);

	// Find active and enabled user (both conditions)
	Optional<User> findByEmailAndIsDeletedFalseAndIsActiveTrue(String email);

	// Check existence (useful for validation)
	boolean existsByEmailAndIsDeletedFalse(String email);

	// Optional: Find by ID and not deleted (safe fetch)
	Optional<User> findByIdAndIsDeletedFalse(Long id);

	// Custom native query (if needed) — but prefer derived queries above
	@Query(value = "SELECT * FROM user u WHERE u.email = :email AND u.is_deleted = false", nativeQuery = true)
	Optional<User> findActiveByEmailNative(@Param("email") String email);

	@Query(value = "SELECT * FROM user u WHERE u.email =:email", nativeQuery = true)
	User findByemail(String email);
}