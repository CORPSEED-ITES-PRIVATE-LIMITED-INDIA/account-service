package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.TdsRegistration;
import com.account.domain.invoice.Invoice;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.domain.estimate.Estimate;
import com.account.repository.projection.TdsCollectionSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TdsRegistrationRepository extends JpaRepository<TdsRegistration, Long> {


    List<TdsRegistration> findAllByUnbilledInvoiceAndIsDeletedFalse(UnbilledInvoice unbilledInvoice);

    Optional<TdsRegistration> findByPaymentReceiptAndIsDeletedFalse(PaymentReceipt paymentReceipt);

    List<TdsRegistration> findAllByEstimateAndIsDeletedFalse(Estimate estimate);

    List<TdsRegistration> findAllByInvoiceAndIsDeletedFalse(
            Invoice invoice
    );
    /**
     * Current business interpretation:
     *
     * PENDING  = TDS approval pending
     * APPROVED = TDS claimed/approved
     *
     * Rejected and soft-deleted records are excluded.
     */
    @Query(
            value = """
                    SELECT
                        COALESCE(
                            SUM(
                                CASE
                                    WHEN status IN ('PENDING', 'APPROVED')
                                    THEN tds_amount
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS totalTdsAmount,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN status = 'PENDING'
                                    THEN tds_amount
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS pendingAmount,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN status = 'APPROVED'
                                    THEN tds_amount
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS claimedAmount,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN status IN ('PENDING', 'APPROVED')
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS totalCount,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN status = 'PENDING'
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS pendingCount,

                        COALESCE(
                            SUM(
                                CASE
                                    WHEN status = 'APPROVED'
                                    THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS claimedCount

                    FROM tds_registration

                    WHERE is_deleted = 0
                      AND status IN ('PENDING', 'APPROVED')
                    """,
            nativeQuery = true
    )
    TdsCollectionSummaryProjection
    getTdsCollectionSummary();

}