package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.GstDataFromCrm;
import com.account.dashboard.domain.GstDetails;

@Repository
public interface GstDataFromCrmRepository extends JpaRepository<GstDataFromCrm, Long> {

}
