package com.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.domain.GstDetails;

@Repository
public interface GstDetailsRepository extends JpaRepository<GstDetails, Long> {

}
