package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.dto.unbilled.UnbilledInvoiceFilterRequest;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;
import com.account.repository.UnbilledInvoiceRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.*;
import org.springframework.stereotype.Service;

import com.account.service.UnbilledService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnbilledServiceImpl implements UnbilledService {

    private final UnbilledInvoiceRepository unbilledInvoiceRepository;

    @Override
    public List<UnbilledInvoiceSummaryDto> searchUnbilledInvoices(
            Long userId,
            UnbilledInvoiceFilterRequest filter,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<UnbilledInvoice> spec = (root, query, cb) -> cb.conjunction();

        if (userId != null) {
            spec = spec.and((root, query, cb) -> {
                var createdByJoin = root.join("createdBy", JoinType.LEFT);
                var approvedByJoin = root.join("approvedBy", JoinType.LEFT);

                return cb.or(
                        cb.equal(createdByJoin.get("id"), userId),
                        cb.equal(approvedByJoin.get("id"), userId)
                );
            });
        }

        if (filter != null) {
            spec = spec
                    .and(searchFilter(filter.getSearch()))
                    .and(statusFilter(filter.getStatus()))
                    .and(dateRangeFilter(filter.getFromDate(), filter.getToDate()))
                    .and(outstandingAmountFilter(filter.getMinOutstandingAmount(), filter.getMaxOutstandingAmount()));
        }


        return unbilledInvoiceRepository.findAll(spec, pageable)
                .getContent()
                .stream()
                .map(this::mapToSummaryDto)
                .toList();
    }
    private Specification<UnbilledInvoice> searchFilter(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }

            query.distinct(true);

            String pattern = "%" + search.toLowerCase() + "%";

            Join<UnbilledInvoice, Company> companyJoin =
                    root.join("company", JoinType.LEFT);

            return cb.or(
                    cb.like(cb.lower(root.get("unbilledNumber")), pattern),
                    cb.like(cb.lower(companyJoin.get("name")), pattern)
            );
        };
    }
    private Specification<UnbilledInvoice> statusFilter(UnbilledStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction()
                        : cb.equal(root.get("status"), status);
    }
    private Specification<UnbilledInvoice> outstandingAmountFilter(
            BigDecimal min,
            BigDecimal max
    ) {
        return (root, query, cb) -> {

            if (min == null && max == null) {
                return cb.conjunction();
            }

            if (min != null && max != null) {
                return cb.between(root.get("outstandingAmount"), min, max);
            }

            if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("outstandingAmount"), min);
            }

            return cb.lessThanOrEqualTo(root.get("outstandingAmount"), max);
        };
    }
    private Specification<UnbilledInvoice> dateRangeFilter(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (root, query, cb) -> {

            if (fromDate == null && toDate == null) {
                return cb.conjunction();
            }

            // Convert LocalDate → LocalDateTime boundaries
            LocalDateTime fromDateTime =
                    fromDate != null ? fromDate.atStartOfDay() : null;

            LocalDateTime toDateTime =
                    toDate != null ? toDate.atTime(LocalTime.MAX) : null;

            if (fromDateTime != null && toDateTime != null) {
                return cb.between(
                        root.get("createdAt"),
                        fromDateTime,
                        toDateTime
                );
            }

            if (fromDateTime != null) {
                return cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        fromDateTime
                );
            }

            return cb.lessThanOrEqualTo(
                    root.get("createdAt"),
                    toDateTime
            );
        };
    }
    private UnbilledInvoiceSummaryDto mapToSummaryDto(UnbilledInvoice unbilled) {
        UnbilledInvoiceSummaryDto dto = new UnbilledInvoiceSummaryDto();

        // Basic unbilled fields
        dto.setId(unbilled.getId());
        dto.setUnbilledNumber(unbilled.getUnbilledNumber());

        // Estimate related
        Estimate estimate = unbilled.getEstimate();
        dto.setEstimateNumber(estimate != null ? estimate.getEstimateNumber() : null);
        dto.setEstimateId(estimate != null ? estimate.getId() : null);
        dto.setSolutionId(estimate != null ? estimate.getSolutionId() : null);
        dto.setSolutionName(estimate != null ? estimate.getSolutionName() : null);

        // Company info
        Company company = unbilled.getCompany();
        dto.setCompanyName(company != null ? company.getName() : null);

        // Contact info (from unbilled → comes from estimate.contact)
        Contact contact = unbilled.getContact();
        dto.setContactName(contact != null ? contact.getName() : null);
        dto.setEmails(contact != null ? contact.getEmails() : null);
        dto.setContactNo(contact != null ? contact.getContactNo() : null);

        // Address & GST fields → come from CompanyUnit (not Company)
        CompanyUnit unit = unbilled.getUnit();
        if (unit != null) {
            dto.setAddressLine1(unit.getAddressLine1());
            dto.setAddressLine2(unit.getAddressLine2());
            dto.setCity(unit.getCity());
            dto.setState(unit.getState());
            dto.setCountry(unit.getCountry() != null ? unit.getCountry() : "India");
            dto.setPinCode(unit.getPinCode());
            dto.setGstNo(unit.getGstNo());
        } else if (company != null) {
            // Fallback: if no unit → try to use company-level address (if you ever add them)
            // Currently Company doesn't have these fields → so most cases will be null
            // You can leave this block empty or add comment
        }

        // Financials
        dto.setTotalAmount(unbilled.getTotalAmount());
        dto.setReceivedAmount(unbilled.getReceivedAmount());
        dto.setOutstandingAmount(unbilled.getOutstandingAmount());

        // Status & audit timestamps
        dto.setStatus(unbilled.getStatus());
        dto.setCreatedAt(unbilled.getCreatedAt());
        dto.setApprovedAt(unbilled.getApprovedAt());

        // Created by (salesperson)
        User createdBy = unbilled.getCreatedBy();
        dto.setCreatedByName(
                createdBy != null
                        ? (createdBy.getFullName() != null ? createdBy.getFullName() : createdBy.getEmail())
                        : null
        );

        // Approved by (accounts person)
        User approvedBy = unbilled.getApprovedBy();
        dto.setApprovedByName(
                approvedBy != null
                        ? (approvedBy.getFullName() != null ? approvedBy.getFullName() : approvedBy.getEmail())
                        : null
        );

        // Fallback/project name
        dto.setName(
                estimate != null && estimate.getSolutionName() != null
                        ? estimate.getSolutionName()
                        : (company != null ? company.getName() + " - Project" : "Unnamed Project")
        );

        return dto;
    }

}

