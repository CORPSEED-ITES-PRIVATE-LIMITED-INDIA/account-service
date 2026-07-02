package com.account.repository;

import com.account.domain.invoice.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {
    // Rarely needed — managed via Invoice cascade
}