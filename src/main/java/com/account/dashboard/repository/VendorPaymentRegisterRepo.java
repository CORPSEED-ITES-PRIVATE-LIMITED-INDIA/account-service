package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.account.dashboard.domain.VendorPaymentRegister;


public interface VendorPaymentRegisterRepo extends JpaRepository<VendorPaymentRegister, Long> {

}
