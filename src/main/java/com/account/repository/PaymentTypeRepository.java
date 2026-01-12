package com.account.repository;

import com.account.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentType, Long> {

    Optional<PaymentType> findByCode(String code); // e.g. "FULL", "PARTIAL"

    boolean existsByCode(String code);
}