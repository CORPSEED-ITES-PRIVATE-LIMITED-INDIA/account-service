package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.account.dashboard.domain.Amount;

public interface AmountRepository extends JpaRepository<Amount, Long> {

}
