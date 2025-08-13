package com.account.dashboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.account.Ledger;

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {

	@Query(value = "SELECT id FROM ledger l WHERE l.id =:id", nativeQuery = true)
	List<Long> findByLedgerTypeId(Long id);

	@Query(value = "SELECT * FROM ledger l WHERE name=:companyName limit 1", nativeQuery = true)
	Ledger findByName(String companyName);
	@Query(value = "SELECT * FROM ledger l WHERE l.name =:searchTerm", nativeQuery = true)
	List<Ledger> findByNameLike(String searchTerm);
	
	@Query(value = "SELECT * FROM ledger l WHERE l.ledger_type_id =:id", nativeQuery = true)
	List<Ledger> findAllByLedgerTypeId(Long id);

	@Query(value = "SELECT count(*) FROM ledger l", nativeQuery = true)
	long findAllCount();


}
