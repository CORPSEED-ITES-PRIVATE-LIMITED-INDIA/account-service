package com.account.repository;

import com.account.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {


	@Query("SELECT u FROM User u WHERE u.email = :email AND u.isDeleted = false")
	Optional<User> findByEmail(@Param("email") String email);

	@Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.isActive = true")
	List<User> findAllActiveUsers();


	// Activate / Deactivate without delete
	@Modifying
	@Query("UPDATE User u SET u.isActive = :active WHERE u.id = :id")
	int setActiveStatus(@Param("id") Long id, @Param("active") boolean active);

	// Quick existence checks
	boolean existsByEmailAndIsDeletedFalse(String email);

	@Query("SELECT COUNT(u) > 0 FROM User u WHERE u.id = :id AND u.isDeleted = false")
	boolean existsByIdAndNotDeleted(@Param("id") Long id);


}