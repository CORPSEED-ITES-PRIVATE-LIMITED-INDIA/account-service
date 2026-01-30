package com.account.repository;

import com.account.domain.Company;
import com.account.domain.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {



    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByPanNoAndIsDeletedFalse(String panNo);

    @Query("""
        SELECT c
        FROM Company c
        WHERE c.id = :id
          AND c.isDeleted = false
    """)
    Optional<Company> findByIdAndIsDeletedFalse(@Param("id") Long id);

    @Query("""
        SELECT c
        FROM Company c
        WHERE c.isDeleted = false
        ORDER BY c.createdAt DESC
    """)
    Page<Company> findAllActive(Pageable pageable);


    @Query("""
        SELECT c
        FROM Company c
        WHERE c.isDeleted = false
          AND c.onboardingStatus = :status
        ORDER BY c.createdAt DESC
    """)
    Page<Company> findByOnboardingStatus(
            @Param("status") OnboardingStatus status,
            Pageable pageable
    );


    @Query("""
        SELECT c
        FROM Company c
        WHERE c.isDeleted = false
          AND c.createdBy.id = :userId
        ORDER BY c.createdAt DESC
    """)
    Page<Company> findByCreatedBy(
            @Param("userId") Long userId,
            Pageable pageable
    );


    @Query("""
        SELECT c
        FROM Company c
        WHERE c.isDeleted = false
          AND c.onboardingStatus = :status
          AND c.createdBy.id = :userId
        ORDER BY c.createdAt DESC
    """)
    Page<Company> findByOnboardingStatusAndCreatedBy(
            @Param("status") OnboardingStatus status,
            @Param("userId") Long userId,
            Pageable pageable
    );
}
