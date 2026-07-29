package com.account.repository.vendor;

import com.account.domain.vendor.ExternalVendor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalVendorRepository
        extends JpaRepository<ExternalVendor, Long> {

    @EntityGraph(attributePaths = {
            "ledger",
            "ledger.ledgerGroup"
    })
    Optional<ExternalVendor>
    findByOperationVendorIdAndDeletedFalse(
            Long operationVendorId
    );
}