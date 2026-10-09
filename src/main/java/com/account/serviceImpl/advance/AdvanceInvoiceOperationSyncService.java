package com.account.serviceImpl.advance;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.invoice.Invoice;
import com.account.domain.invoice.OperationSyncStatus;
import com.account.dto.operationService.AdvanceInvoiceOperationProjectRequestDto;
import com.account.dto.operationService.AdvanceInvoiceOperationProjectResponseDto;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.InvoiceRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdvanceInvoiceOperationSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(AdvanceInvoiceOperationSyncService.class);

    /*
     * Existing normal/document money precision.
     *
     * Do not use this helper for taxable/GST values because
     * taxable and GST must preserve three-decimal precision.
     */
    private static final int MONEY_SCALE = 2;

    /*
     * Taxable and GST precision.
     */
    private static final int TAX_SCALE = 3;
    private static final int GST_SCALE = 3;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_UP;

    private final InvoiceRepository invoiceRepository;
    private final OperationFeignClient operationFeignClient;


    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void synchronize(
            Long invoiceId,
            Long confirmedByUserId
    ) {

        Invoice invoice =
                invoiceRepository
                        .findById(invoiceId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Invoice not found during Operation synchronization: "
                                                + invoiceId
                                )
                        );

        if (invoice.isOperationSynced()
                && hasText(
                invoice.getOperationProjectNo()
        )) {
            return;
        }

        Estimate estimate =
                invoice.getEstimate();

        if (estimate == null
                || estimate.getId() == null) {

            markRetry(
                    invoice,
                    "Estimate is missing for Operation synchronization"
            );

            return;
        }

        try {

            ResponseEntity<AdvanceInvoiceOperationProjectResponseDto>
                    existingResponse =
                    operationFeignClient
                            .getProjectByEstimateId(
                                    estimate.getId()
                            );

            if (existingResponse != null
                    && existingResponse
                    .getStatusCode()
                    .is2xxSuccessful()
                    && existingResponse.getBody() != null) {

                markSynced(
                        invoice,
                        existingResponse
                                .getBody()
                                .getProjectNo()
                );

                log.info(
                        "Existing Operation Project linked | invoiceId={} | estimateId={} | projectNo={}",
                        invoiceId,
                        estimate.getId(),
                        existingResponse
                                .getBody()
                                .getProjectNo()
                );

                return;
            }

        } catch (FeignException.NotFound ignored) {

            /*
             * Continue to create-or-sync endpoint.
             */

        } catch (Exception ex) {

            markRetry(
                    invoice,
                    safeError(ex)
            );

            return;
        }

        try {

            AdvanceInvoiceOperationProjectRequestDto request =
                    buildRequest(
                            invoice,
                            estimate,
                            confirmedByUserId
                    );

            ResponseEntity<AdvanceInvoiceOperationProjectResponseDto>
                    response =
                    operationFeignClient
                            .createOrSyncProjectFromAdvanceInvoice(
                                    request
                            );

            if (response == null
                    || !response
                    .getStatusCode()
                    .is2xxSuccessful()
                    || response.getBody() == null
                    || !hasText(
                    response
                            .getBody()
                            .getProjectNo()
            )) {

                markRetry(
                        invoice,
                        "Operation Service returned an invalid project response"
                );

                return;
            }

            markSynced(
                    invoice,
                    response
                            .getBody()
                            .getProjectNo()
            );

            log.info(
                    "Operation Project synchronized | invoiceId={} | estimateId={} | projectNo={}",
                    invoiceId,
                    estimate.getId(),
                    response
                            .getBody()
                            .getProjectNo()
            );

        } catch (Exception ex) {

            markRetry(
                    invoice,
                    safeError(ex)
            );

            log.error(
                    "Operation Project synchronization failed | invoiceId={} | estimateId={} | error={}",
                    invoiceId,
                    estimate.getId(),
                    safeError(ex),
                    ex
            );
        }
    }

    private AdvanceInvoiceOperationProjectRequestDto buildRequest(
            Invoice invoice,
            Estimate estimate,
            Long confirmedByUserId
    ) {

        GstRegistrationType gstType =
                invoice.getGstRegistrationType();

        return AdvanceInvoiceOperationProjectRequestDto
                .builder()

                .idempotencyKey(
                        "ADVANCE-INVOICE-"
                                + invoice.getId()
                )

                .estimateId(
                        estimate.getId()
                )

                .estimateNumber(
                        estimate.getEstimateNumber()
                )

                .leadId(
                        estimate.getLeadId()
                )

                .companyId(
                        estimate.getCompany() != null
                                ? estimate
                                .getCompany()
                                .getId()
                                : null
                )

                .companyName(
                        estimate.getCompany() != null
                                ? estimate
                                .getCompany()
                                .getName()
                                : null
                )

                .unitId(
                        estimate.getUnit() != null
                                ? estimate
                                .getUnit()
                                .getId()
                                : null
                )

                .unitName(
                        estimate.getUnit() != null
                                ? estimate
                                .getUnit()
                                .getUnitName()
                                : null
                )

                .contactId(
                        estimate.getContact() != null
                                ? estimate
                                .getContact()
                                .getId()
                                : null
                )

                .solutionId(
                        invoice.getSolutionId()
                )

                .solutionName(
                        invoice.getSolutionName()
                )

                .invoiceId(
                        invoice.getId()
                )

                .invoiceNumber(
                        invoice.getInvoiceNumber()
                )

                .invoiceOrigin(
                        invoice.getInvoiceOrigin()
                )

                .invoiceDate(
                        invoice.getInvoiceDate()
                )

                /*
                 * IMPORTANT:
                 *
                 * Taxable amount must preserve three decimals.
                 *
                 * Example:
                 * 100.125 remains 100.125
                 * and must not become 100.13.
                 */
                .taxableAmount(
                        taxMoney(
                                invoice.getSubTotalExGst()
                        )
                )

                /*
                 * IMPORTANT:
                 *
                 * GST must never be modified because of
                 * document-level rounding.
                 *
                 * Example:
                 * 18.125 remains 18.125
                 * and must not become 18.13.
                 */
                .gstAmount(
                        gstMoney(
                                invoice.getTotalGstAmount()
                        )
                )

                /*
                 * Keep existing grand-total sync behavior unchanged.
                 */
                .grandTotal(
                        money(
                                invoice.getGrandTotal()
                        )
                )

                .gstRegistrationType(
                        gstType
                )

                .eInvoiceRequired(
                        isEInvoiceRequired(
                                gstType
                        )
                )

                .eInvoiceIrn(
                        isEInvoiceRequired(gstType)
                                ? invoice.getEInvoiceIrn()
                                : null
                )

                .eInvoiceAckNo(
                        isEInvoiceRequired(gstType)
                                ? invoice.getEInvoiceAckNo()
                                : null
                )

                .eInvoiceAckDate(
                        isEInvoiceRequired(gstType)
                                ? invoice.getEInvoiceAckDate()
                                : null
                )

                .confirmedByUserId(
                        confirmedByUserId
                )

                .build();
    }

    private void markSynced(
            Invoice invoice,
            String projectNo
    ) {

        invoice.setOperationSynced(
                true
        );

        invoice.setOperationSyncedAt(
                LocalDateTime.now()
        );

        invoice.setOperationProjectNo(
                projectNo
        );

        invoice.setOperationSyncStatus(
                OperationSyncStatus.SYNCED
        );

        invoice.setOperationLastError(
                null
        );

        invoice.setOperationNextRetryAt(
                null
        );

        invoiceRepository.save(
                invoice
        );
    }

    private void markRetry(
            Invoice invoice,
            String error
    ) {

        invoice.setOperationSynced(
                false
        );

        invoice.setOperationSyncStatus(
                OperationSyncStatus.RETRY_PENDING
        );

        invoice.setOperationLastError(
                limit(
                        error,
                        1000
                )
        );

        invoice.setOperationSyncAttempts(
                invoice.getOperationSyncAttempts() + 1
        );

        invoice.setOperationNextRetryAt(
                LocalDateTime
                        .now()
                        .plusMinutes(5)
        );

        invoiceRepository.save(
                invoice
        );
    }

    private boolean isEInvoiceRequired(
            GstRegistrationType type
    ) {

        return type == GstRegistrationType.REGISTERED
                || type == GstRegistrationType.SEZ;
    }

    /*
     * =========================================================
     * NORMAL / DOCUMENT MONEY
     * =========================================================
     *
     * Existing behavior intentionally retained.
     */
    private BigDecimal money(
            BigDecimal value
    ) {

        return value == null
                ? BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        )
                : value.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }

    /*
     * =========================================================
     * TAXABLE AMOUNT
     * =========================================================
     *
     * Taxable amount is retained at three decimal places.
     */
    private BigDecimal taxMoney(
            BigDecimal value
    ) {

        return value == null
                ? BigDecimal.ZERO.setScale(
                TAX_SCALE,
                ROUNDING_MODE
        )
                : value.setScale(
                TAX_SCALE,
                ROUNDING_MODE
        );
    }

    /*
     * =========================================================
     * GST AMOUNT
     * =========================================================
     *
     * GST is retained at three decimal places.
     *
     * Document round-off must NEVER be absorbed into GST.
     */
    private BigDecimal gstMoney(
            BigDecimal value
    ) {

        return value == null
                ? BigDecimal.ZERO.setScale(
                GST_SCALE,
                ROUNDING_MODE
        )
                : value.setScale(
                GST_SCALE,
                ROUNDING_MODE
        );
    }

    private boolean hasText(
            String value
    ) {

        return value != null
                && !value
                .trim()
                .isEmpty();
    }

    private String safeError(
            Throwable throwable
    ) {

        if (throwable == null
                || !hasText(
                throwable.getMessage()
        )) {

            return "Operation Service synchronization failed";
        }

        return limit(
                throwable
                        .getMessage()
                        .replaceAll(
                                "[\\r\\n\\t]+",
                                " "
                        ),
                500
        );
    }

    private String limit(
            String value,
            int max
    ) {

        if (value == null
                || value.length() <= max) {

            return value;
        }

        return value.substring(
                0,
                max
        );
    }
}