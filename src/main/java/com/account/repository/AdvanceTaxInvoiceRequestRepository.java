package com.account.repository;

import com.account.domain.estimate.Estimate;
import com.account.domain.invoice.AdvanceTaxInvoiceRequest;
import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface AdvanceTaxInvoiceRequestRepository
        extends JpaRepository<AdvanceTaxInvoiceRequest, Long> {

    boolean existsByEstimateAndStatus(
            Estimate estimate,
            AdvanceTaxInvoiceRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from AdvanceTaxInvoiceRequest request
            join fetch request.estimate estimate
            left join fetch request.requestedBy
            left join fetch request.reviewedBy
            where request.id = :requestId
            """)
    Optional<AdvanceTaxInvoiceRequest> findByIdForUpdate(
            @Param("requestId") Long requestId
    );


    @Query("""
            select coalesce(sum(request.requestedAmount), 0)
            from AdvanceTaxInvoiceRequest request
            where request.estimate = :estimate
              and request.status = :status
            """)
    BigDecimal sumAmountByEstimateAndStatus(
            @Param("estimate") Estimate estimate,
            @Param("status") AdvanceTaxInvoiceRequestStatus status
    );


    @Query("""
            SELECT request
            FROM AdvanceTaxInvoiceRequest request
            WHERE (
                :requestedByUserId IS NULL
                OR request.requestedBy.id = :requestedByUserId
            )
            AND (
                :status IS NULL
                OR request.status = :status
            )
            """)
    Page<AdvanceTaxInvoiceRequest> findVisibleRequests(
            @Param("requestedByUserId")
            Long requestedByUserId,

            @Param("status")
            AdvanceTaxInvoiceRequestStatus status,

            Pageable pageable
    );
}
