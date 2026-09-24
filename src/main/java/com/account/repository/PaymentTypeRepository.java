package com.account.repository;

import com.account.domain.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentType, Long> {

    boolean existsByCodeAndIsDeletedFalse(String code);
    boolean existsByNameAndIsDeletedFalse(String name);

    List<PaymentType> findByIsDeletedFalse();

    @Modifying
    @Query("UPDATE PaymentType p SET p.isDeleted = true, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id AND p.isDeleted = false")
    int softDeleteById(Long id);

    boolean existsByCode(String code);

}