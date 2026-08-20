package com.account.repository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class InvoiceFeedRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String BASE_UNION_SQL = """
        SELECT
            'INVOICE' AS record_type,
            i.id AS id,
            i.public_uuid AS public_uuid,
            i.invoice_number AS reference_number,
            e.estimate_number AS estimate_number,
            COALESCE(c1.name, c2.name) AS company_name,
            i.solution_name AS solution_name,
            i.grand_total AS amount,
            i.currency AS currency,
            i.status AS invoice_status,
            NULL AS advance_request_status,
            i.gst_registration_type AS gst_registration_type,
            COALESCE(u1.full_name, u1.email) AS created_by_name,
            i.created_at AS created_at,
            i.invoice_date AS invoice_date
        FROM invoice i
        LEFT JOIN estimate e ON e.id = i.estimate_id
        LEFT JOIN unbilled_invoice ub ON ub.id = i.unbilled_invoice_id
        LEFT JOIN company c1 ON c1.id = ub.company_id
        LEFT JOIN company c2 ON c2.id = e.company_id
        LEFT JOIN user u1 ON u1.id = i.created_by
        WHERE i.is_cancelled = false
          AND (:createdById IS NULL OR i.created_by = :createdById)
          AND (:fromDate IS NULL OR i.invoice_date >= :fromDate)
          AND (:toDate IS NULL OR i.invoice_date <= :toDate)

        UNION ALL

        SELECT
            'ADVANCE_REQUEST' AS record_type,
            r.id AS id,
            r.public_uuid AS public_uuid,
            r.public_uuid AS reference_number,
            e.estimate_number AS estimate_number,
            c.name AS company_name,
            e.solution_name AS solution_name,
            COALESCE(r.approved_amount, r.requested_amount) AS amount,
            'INR' AS currency,
            NULL AS invoice_status,
            r.status AS advance_request_status,
            e.gst_registration_type AS gst_registration_type,
            COALESCE(u2.full_name, u2.email) AS created_by_name,
            r.created_at AS created_at,
            NULL AS invoice_date
        FROM advance_tax_invoice_request r
        LEFT JOIN estimate e ON e.id = r.estimate_id
        LEFT JOIN company c ON c.id = e.company_id
        LEFT JOIN user u2 ON u2.id = r.requested_by
        WHERE (:requestedById IS NULL OR r.requested_by = :requestedById)
          AND (:fromDateTime IS NULL OR r.created_at >= :fromDateTime)
          AND (:toDateTime IS NULL OR r.created_at <= :toDateTime)
        """;

    @SuppressWarnings("unchecked")
    public List<Object[]> findFeedPage(
            Long userId,
            String filter,
            LocalDate fromDate,
            LocalDate toDate,
            int offset,
            int limit
    ) {
        String sql = buildSql(filter) + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset";

        Query query = entityManager.createNativeQuery(sql);
        bindParams(query, userId, filter, fromDate, toDate);
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);

        return query.getResultList();
    }

    public long countFeed(
            Long userId,
            String filter,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String sql = "SELECT COUNT(*) FROM (" + buildSql(filter) + ") AS feed";

        Query query = entityManager.createNativeQuery(sql);
        bindParams(query, userId, filter, fromDate, toDate);

        Object result = query.getSingleResult();
        return ((Number) result).longValue();
    }

    private String buildSql(String filter) {
        if ("INVOICE".equals(filter)) {
            return extractInvoiceSelect();
        }
        if ("ADVANCE_REQUEST".equals(filter)) {
            return extractAdvanceRequestSelect();
        }
        return BASE_UNION_SQL;
    }

    private String extractInvoiceSelect() {
        return BASE_UNION_SQL.substring(0, BASE_UNION_SQL.indexOf("UNION ALL"));
    }

    private String extractAdvanceRequestSelect() {
        return BASE_UNION_SQL.substring(BASE_UNION_SQL.indexOf("UNION ALL") + "UNION ALL".length());
    }

    private void bindParams(
            Query query,
            Long userId,
            String filter,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        boolean includesInvoice = !"ADVANCE_REQUEST".equals(filter);
        boolean includesAdvance = !"INVOICE".equals(filter);

        if (includesInvoice) {
            query.setParameter("createdById", userId);
            query.setParameter("fromDate", fromDate);
            query.setParameter("toDate", toDate);
        }

        if (includesAdvance) {
            query.setParameter("requestedById", userId);
            query.setParameter("fromDateTime", fromDate != null ? fromDate.atStartOfDay() : null);
            query.setParameter("toDateTime", toDate != null ? toDate.atTime(23, 59, 59) : null);
        }
    }
}
