package com.account.repository;

import com.account.domain.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentType, Long> {

    Optional<PaymentType> findByCodeAndIsDeletedFalse(String code);

    boolean existsByCodeAndIsDeletedFalse(String code);

    boolean existsByNameAndIsDeletedFalse(String name);

    @Query("SELECT p FROM PaymentType p WHERE p.isDeleted = false")
    Page<PaymentType> findByIsDeletedFalse(Pageable pageable);

    @Modifying
    @Query("UPDATE PaymentType p SET p.isDeleted = true, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id AND p.isDeleted = false")
    int softDeleteById(Long id);

    boolean existsByCode(String code);
}