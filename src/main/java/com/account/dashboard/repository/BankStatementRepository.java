package com.account.dashboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.BankStatement;

@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

	@Query(value = "SELECT * FROM bank_statement bs WHERE bs.left_amount >0", nativeQuery = true)
	List<BankStatement> findAllByLeftAmount();

}
