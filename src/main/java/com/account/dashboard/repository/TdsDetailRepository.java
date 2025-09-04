package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.account.dashboard.domain.Role;
import com.account.dashboard.domain.TdsDetail;

public interface TdsDetailRepository extends JpaRepository<TdsDetail, Long> {

}
