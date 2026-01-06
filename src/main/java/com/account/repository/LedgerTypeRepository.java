package com.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.domain.account.LedgerType;

@Repository
public interface LedgerTypeRepository extends JpaRepository<LedgerType, Long> {

	@Query(value = "SELECT * FROM ledger_type l WHERE l.is_deleted =:b", nativeQuery = true)
	List<LedgerType> findAllByIsDeleted(boolean b);
	@Query(value = "SELECT * FROM ledger_type l WHERE l.name =:name limit 1", nativeQuery = true)
	LedgerType findByName(String name);
	
	@Query(value = "SELECT * FROM ledger_type l WHERE l.name in(:name)", nativeQuery = true)
	List<LedgerType> findByNameIn(List<String> name);
	
	@Query(value = "SELECT * FROM ledger_type l WHERE l.name LIKE %:searchTerm%", nativeQuery = true)
	List<LedgerType> findByNameGlobal(String searchTerm);
	
	@Query(value = "SELECT * FROM ledger_type l WHERE l.name in(:name)", nativeQuery = true)
	List<Long> findIdByNameIn(List<String> name);
	
	@Query(value = "SELECT * FROM ledger_type l WHERE l.name =:name limit 1", nativeQuery = true)
	Long findIdByName(String name);
	
	@Query(value = "SELECT l.id FROM ledger_type l WHERE l.name in(:name)", nativeQuery = true)
	List<Long> findIdByledgerTypeId(List<String> name);
	
	@Query(value = "SELECT l.id FROM ledger_type l WHERE l.ledger_type_id in(:id)", nativeQuery = true)
	List<Long> findIdByParentIdIn(List<Long> id);
	@Query(value = "SELECT * FROM ledger_type l WHERE l.id in(:list)", nativeQuery = true)
	List<LedgerType> findAllByIdIn(List<Long> list);
	
	@Query(value = "SELECT l.id FROM ledger_type l WHERE l.ledger_type_id =:id", nativeQuery = true)
	List<Long> findIdByParentId(Long id);
}
