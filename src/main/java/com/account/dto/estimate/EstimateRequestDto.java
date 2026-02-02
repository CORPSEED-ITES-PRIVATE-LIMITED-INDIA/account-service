package com.account.dto.estimate;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EstimateRequestDto {

    private LocalDate purchaseDate;

    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String productName;

    @NotNull(message = "Company ID is required")
    private Long companyId;

    private Long unitId;     // Optional
    private Long contactId;  // Optional

    /* ================= Billing Address ================= */

    @Size(max = 255)
    private String billingAddressLine1;

    @Size(max = 255)
    private String billingAddressLine2;

    @Size(max = 100)
    private String billingCity;

    @Size(max = 100)
    private String billingState;

    @Size(max = 100)
    private String billingCountry = "India";

    @Size(max = 10)
    private String billingPinCode;

    /* ================= Shipping Address ================= */

    @Size(max = 255)
    private String shippingAddressLine1;

    @Size(max = 255)
    private String shippingAddressLine2;

    @Size(max = 100)
    private String shippingCity;

    @Size(max = 100)
    private String shippingState;

    @Size(max = 100)
    private String shippingCountry = "India";

    @Size(max = 10)
    private String shippingPinCode;

    /* ================= Other Info ================= */

    @Size(max = 1000)
    private String remark;

    @Size(max = 20)
    private String employeeCode;

    /* ================= Pricing ================= */

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal tax = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal governmentFee = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal cgst = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal sgst = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal professionalFee = BigDecimal.ZERO;

    /* ================= Status ================= */

    private String status = "Draft"; // Optional override
}
