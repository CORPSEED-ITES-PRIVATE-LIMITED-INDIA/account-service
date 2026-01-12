package com.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.domain.Unbilled;

@Repository
public interface UnbilledRepository extends JpaRepository<Unbilled, Long> {


}