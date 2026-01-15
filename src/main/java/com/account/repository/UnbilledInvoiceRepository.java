package com.account.repository;

import com.account.domain.UnbilledInvoice;
import com.account.domain.UnbilledStatus;
import com.account.domain.estimate.Estimate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnbilledInvoiceRepository extends JpaRepository<UnbilledInvoice, Long> {

    Optional<UnbilledInvoice> findByEstimate(Estimate estimate);

    Page<UnbilledInvoice> findByCreatedByIdOrApprovedById(
            Long createdById,
            Long approvedById,
            Pageable pageable);

    Page<UnbilledInvoice> findByStatus(
            UnbilledStatus status,
            Pageable pageable);

    Page<UnbilledInvoice> findByCreatedByIdOrApprovedByIdAndStatus(
            Long createdById,
            Long approvedById,
            UnbilledStatus status,
            Pageable pageable);

    @Query("""
        SELECT u
        FROM UnbilledInvoice u
        WHERE
            (:userId IS NULL
                OR u.createdBy.id = :userId
                OR u.approvedBy.id = :userId)
        AND
            (:status IS NULL OR u.status = :status)
        """)
    Page<UnbilledInvoice> findUnbilledInvoices(
            @Param("userId") Long userId,
            @Param("status") UnbilledStatus status,
            Pageable pageable
    );

    long countByCreatedByIdOrApprovedById(Long createdById, Long approvedById);

    long countByStatus(UnbilledStatus status);

    long countByCreatedByIdOrApprovedByIdAndStatus(Long createdById, Long approvedById, UnbilledStatus status);




}
