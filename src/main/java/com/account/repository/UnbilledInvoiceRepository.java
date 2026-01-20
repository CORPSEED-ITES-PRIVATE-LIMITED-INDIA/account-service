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

    @Query("""
        SELECT u FROM UnbilledInvoice u
        LEFT JOIN u.company c
        WHERE (:unbilledNumber IS NULL OR LOWER(u.unbilledNumber) LIKE LOWER(CONCAT('%', :unbilledNumber, '%')))
        AND (:companyName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :companyName, '%')))
        """)
    Page<UnbilledInvoice> searchUnbilledInvoices(
            @Param("unbilledNumber") String unbilledNumber,
            @Param("companyName") String companyName,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(u) FROM UnbilledInvoice u
        LEFT JOIN u.company c
        WHERE (:unbilledNumber IS NULL OR LOWER(u.unbilledNumber) LIKE LOWER(CONCAT('%', :unbilledNumber, '%')))
        AND (:companyName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :companyName, '%')))
        """)
    long countSearchUnbilledInvoices(
            @Param("unbilledNumber") String unbilledNumber,
            @Param("companyName") String companyName
    );



}
