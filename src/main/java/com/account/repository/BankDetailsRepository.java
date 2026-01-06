package com.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.domain.BankDetails;

@Repository
public interface BankDetailsRepository extends JpaRepository<BankDetails, Long> {

}
