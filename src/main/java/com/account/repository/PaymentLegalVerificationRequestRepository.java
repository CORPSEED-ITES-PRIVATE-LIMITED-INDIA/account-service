package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.payment.PaymentLegalVerificationRequest;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.enm.PaymentLegalVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentLegalVerificationRequestRepository
        extends JpaRepository<PaymentLegalVerificationRequest, Long> {

    Optional<PaymentLegalVerificationRequest>
    findByPaymentReceiptAndIsDeletedFalse(PaymentReceipt paymentReceipt);

    Optional<PaymentLegalVerificationRequest>
    findByPaymentReceiptIdAndIsDeletedFalse(Long paymentReceiptId);

    List<PaymentLegalVerificationRequest>
    findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(PaymentLegalVerificationStatus status);

    List<PaymentLegalVerificationRequest>
    findByUnbilledInvoiceAndIsDeletedFalseOrderByCreatedAtDesc(UnbilledInvoice unbilledInvoice);

    boolean existsByPaymentReceiptIdAndStatusAndIsDeletedFalse(
            Long paymentReceiptId,
            PaymentLegalVerificationStatus status
    );

    interface PaymentLegalStatusCountProjection {
        PaymentLegalVerificationStatus getStatus();
        Long getTotal();
    }

    @Query("""
    SELECT r.status AS status, COUNT(r) AS total
    FROM PaymentLegalVerificationRequest r
    WHERE r.isDeleted = false
    GROUP BY r.status
    """)
    List<PaymentLegalStatusCountProjection> countGroupedByStatus();
}