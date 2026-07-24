package com.account.repository;

import com.account.domain.vendor.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByOperationVendorId(Long operationVendorId);

    boolean existsByOperationVendorId(Long operationVendorId);

    Optional<Vendor> findByGstNumberIgnoreCase(String gstNumber);

    Optional<Vendor> findByPanIgnoreCase(String pan);
}