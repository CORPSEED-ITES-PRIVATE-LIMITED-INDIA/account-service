package com.account.dto.estimate;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EstimateResponseDto {

    private Long id;
    private String orderNumber;
    private LocalDate purchaseDate;
    private String productName;
    private String status; // Draft, Sent, Approved, etc.
    private String remark;
    private String employeeCode;

    /* ================= Company Details ================= */

    private Long companyId;
    private String companyName;
    private String companyPanNo;
    private String companyGstNo;

    /* ================= Unit Details ================= */

    private Long unitId;
    private String unitName;
    private String unitCity;
    private String unitState;
    private String unitGstNo;

    /* ================= Contact Details ================= */

    private Long contactId;
    private String contactName;
    private String contactEmails;
    private String contactNo;
    private String whatsappNo;

    /* ================= Addresses ================= */

    private AddressDto billingAddress;
    private AddressDto shippingAddress;

    /* ================= Pricing Breakdown ================= */

    private BigDecimal basePrice;
    private BigDecimal professionalFee;
    private BigDecimal serviceCharge;
    private BigDecimal governmentFee;
    private BigDecimal tax;
    private BigDecimal cgst;
    private BigDecimal sgst;
    private BigDecimal totalPrice;

    /* ================= Audit Info ================= */

    private String createdByName;
    private LocalDateTime createdAt;
    private String updatedByName;
    private LocalDateTime updatedAt;

    /* ================= Address DTO ================= */

    @Data
    public static class AddressDto {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String country;
        private String pinCode;

        /**
         * Helper to build full address string (useful for PDF / email)
         */
        public String getFullAddress() {
            StringBuilder sb = new StringBuilder();

            if (line1 != null && !line1.isBlank()) sb.append(line1).append(", ");
            if (line2 != null && !line2.isBlank()) sb.append(line2).append(", ");
            if (city != null && !city.isBlank()) sb.append(city).append(", ");
            if (state != null && !state.isBlank()) sb.append(state).append(" ");
            if (pinCode != null && !pinCode.isBlank()) sb.append(pinCode);
            if (country != null && !country.isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(country);
            }

            return sb.toString().replaceAll(", $", "").trim();
        }
    }
}
