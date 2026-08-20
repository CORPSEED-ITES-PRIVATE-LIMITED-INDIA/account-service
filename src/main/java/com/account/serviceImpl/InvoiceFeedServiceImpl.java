package com.account.serviceImpl;

import com.account.domain.User;
import com.account.enm.InvoiceFeedFilter;
import com.account.dto.invoice.InvoiceFeedItemDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.InvoiceFeedRepository;
import com.account.repository.UserRepository;
import com.account.service.InvoiceFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceFeedServiceImpl implements InvoiceFeedService {

    private final InvoiceFeedRepository invoiceFeedRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceFeedItemDto> getFeed(
            Long userId,
            InvoiceFeedFilter filter,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        Long visibleUserId = resolveVisibleUserId(userId);
        String filterValue = (filter == null ? InvoiceFeedFilter.ALL : filter).name();

        int offset = page * size;

        List<Object[]> rows = invoiceFeedRepository.findFeedPage(
                visibleUserId, filterValue, fromDate, toDate, offset, size
        );

        return rows.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getFeedCount(
            Long userId,
            InvoiceFeedFilter filter,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Long visibleUserId = resolveVisibleUserId(userId);
        String filterValue = (filter == null ? InvoiceFeedFilter.ALL : filter).name();

        return invoiceFeedRepository.countFeed(visibleUserId, filterValue, fromDate, toDate);
    }

    private Long resolveVisibleUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException(
                    "Valid userId is required",
                    "ERR_INVALID_USER_ID",
                    "userId"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));

        boolean unrestricted = isAdmin(user) || isAccountsDepartment(user);
        return unrestricted ? null : userId;
    }

    private InvoiceFeedItemDto toDto(Object[] row) {
        return InvoiceFeedItemDto.builder()
                .recordType(asString(row[0]))
                .id(asLong(row[1]))
                .publicUuid(asString(row[2]))
                .referenceNumber(asString(row[3]))
                .estimateNumber(asString(row[4]))
                .companyName(asString(row[5]))
                .solutionName(asString(row[6]))
                .amount(asBigDecimal(row[7]))
                .currency(asString(row[8]))
                .invoiceStatus(asString(row[9]))
                .advanceRequestStatus(asString(row[10]))
                .gstRegistrationType(asString(row[11]))
                .createdByName(asString(row[12]))
                .createdAt(asLocalDateTime(row[13]))
                .invoiceDate(asLocalDate(row[14]))
                .build();
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long asLong(Object value) {
        return value != null ? ((Number) value).longValue() : null;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private LocalDate asLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        return null;
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && user.getUserRole().stream()
                .filter(Objects::nonNull)
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
    }

    private boolean isAccountsDepartment(User user) {
        return user.getDepartment() != null
                && ("ACCOUNT".equalsIgnoreCase(user.getDepartment().trim())
                || "ACCOUNTS".equalsIgnoreCase(user.getDepartment().trim()));
    }
}