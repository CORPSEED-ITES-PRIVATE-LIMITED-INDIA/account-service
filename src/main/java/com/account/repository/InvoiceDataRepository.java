package com.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.domain.InvoiceData;

@Repository
public interface InvoiceDataRepository extends JpaRepository<InvoiceData, Long> {

	@Query(value = "SELECT count(*) FROM invoice_data id", nativeQuery = true)
	long findAllCount();

}
