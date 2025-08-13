package com.account.dashboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.account.LedgerType;

@Repository
public interface LedgerTypeRepository extends JpaRepository<LedgerType, Long> {

	@Query(value = "SELECT * FROM ledger_type l WHERE l.is_deleted =:b", nativeQuery = true)
	List<LedgerType> findAllByIsDeleted(boolean b);
	@Query(value = "SELECT * FROM ledger_type l WHERE l.name =:name", nativeQuery = true)
	LedgerType findByName(String name);
	
	@Query(value = "SELECT * FROM ledger_type l WHERE l.name LIKE %:searchTerm%", nativeQuery = true)
	List<LedgerType> findByNameGlobal(String searchTerm);

}
