package com.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.account.domain.Amount;

public interface AmountRepository extends JpaRepository<Amount, Long> {

}
