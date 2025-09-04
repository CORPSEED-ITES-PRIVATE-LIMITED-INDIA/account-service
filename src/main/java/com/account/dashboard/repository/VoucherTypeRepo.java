package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.account.VoucherType;
@Repository
public interface VoucherTypeRepo extends JpaRepository<VoucherType, Long> {

	@Query(value = "SELECT * FROM voucher_type v WHERE v.name =:registerBy", nativeQuery = true)
	VoucherType findByName(String registerBy);

}
