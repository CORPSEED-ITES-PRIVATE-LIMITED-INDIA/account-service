package com.account.repository;

import com.account.domain.ManageSales;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AccountRepository extends JpaRepository<ManageSales, Long> {

	ManageSales findByProjectId(Long projectId);

}
