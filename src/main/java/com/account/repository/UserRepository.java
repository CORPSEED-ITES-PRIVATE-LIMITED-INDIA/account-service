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

	@Query("""
        SELECT u
        FROM User u
        WHERE u.email = :email
          AND u.isDeleted = false
    """)
	Optional<User> findByEmail(@Param("email") String email);

	@Query("SELECT COUNT(u) > 0 FROM User u WHERE u.id = :id AND u.isDeleted = false")
	boolean existsByIdAndNotDeleted(@Param("id") Long id);

	@Query("""
        SELECT u
        FROM User u
        WHERE u.id = :id
          AND u.isDeleted = false
    """)
	Optional<User> findByIdAndNotDeleted(@Param("id") Long id);

}
