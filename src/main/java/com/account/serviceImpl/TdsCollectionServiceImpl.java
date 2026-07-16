package com.account.serviceImpl;

import com.account.tds.TdsCollectionSummaryDto;
import com.account.repository.TdsRegistrationRepository;
import com.account.repository.projection.TdsCollectionSummaryProjection;
import com.account.service.TdsCollectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TdsCollectionServiceImpl
        implements TdsCollectionService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TdsCollectionServiceImpl.class
            );

    private final TdsRegistrationRepository
            tdsRegistrationRepository;

    @Override
    @Transactional(readOnly = true)
    public TdsCollectionSummaryDto
    getTdsCollectionSummary() {

        log.debug(
                "Fetching client TDS collection summary"
        );

        TdsCollectionSummaryProjection projection =
                tdsRegistrationRepository
                        .getTdsCollectionSummary();

        BigDecimal totalTdsAmount =
                safeMoney(
                        projection != null
                                ? projection.getTotalTdsAmount()
                                : null
                );

        BigDecimal pendingAmount =
                safeMoney(
                        projection != null
                                ? projection.getPendingAmount()
                                : null
                );

        BigDecimal claimedAmount =
                safeMoney(
                        projection != null
                                ? projection.getClaimedAmount()
                                : null
                );

        Long totalCount =
                safeLong(
                        projection != null
                                ? projection.getTotalCount()
                                : null
                );

        Long pendingCount =
                safeLong(
                        projection != null
                                ? projection.getPendingCount()
                                : null
                );

        Long claimedCount =
                safeLong(
                        projection != null
                                ? projection.getClaimedCount()
                                : null
                );

        /*
         * Defensive validation.
         *
         * totalTdsAmount should always equal:
         *
         * pendingAmount + claimedAmount
         */
        BigDecimal calculatedTotal =
                pendingAmount
                        .add(claimedAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (totalTdsAmount.compareTo(calculatedTotal) != 0) {

            log.warn(
                    "TDS summary mismatch detected | " +
                            "repositoryTotal={} | " +
                            "calculatedTotal={} | " +
                            "pending={} | claimed={}",
                    totalTdsAmount,
                    calculatedTotal,
                    pendingAmount,
                    claimedAmount
            );

            /*
             * Use status breakup as the source of truth.
             */
            totalTdsAmount = calculatedTotal;
        }

        TdsCollectionSummaryDto response =
                TdsCollectionSummaryDto.builder()
                        .totalTdsAmount(totalTdsAmount)
                        .pendingAmount(pendingAmount)
                        .claimedAmount(claimedAmount)
                        .totalCount(totalCount)
                        .pendingCount(pendingCount)
                        .claimedCount(claimedCount)
                        .build();

        log.info(
                "Client TDS summary fetched | " +
                        "totalTdsAmount={} | " +
                        "pendingAmount={} | " +
                        "claimedAmount={} | " +
                        "totalCount={} | " +
                        "pendingCount={} | " +
                        "claimedCount={}",
                response.getTotalTdsAmount(),
                response.getPendingAmount(),
                response.getClaimedAmount(),
                response.getTotalCount(),
                response.getPendingCount(),
                response.getClaimedCount()
        );

        return response;
    }

    private BigDecimal safeMoney(
            BigDecimal value
    ) {

        return value == null
                ? BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        )
                : value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private Long safeLong(
            Long value
    ) {

        return value == null
                ? 0L
                : value;
    }
}