package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.account.dashboard.domain.ManageSales;


public interface AccountRepository extends JpaRepository<ManageSales, Long> {

	ManageSales findByProjectId(Long projectId);

}
