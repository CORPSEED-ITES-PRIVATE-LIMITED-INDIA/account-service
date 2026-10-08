package com.account.serviceImpl.email;

import com.account.domain.Contact;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Small, stateless helpers shared by InvoiceEmailComposer, InvoicePdfService
 * and InvoiceEmailService, so the email and the PDF always show the same
 * client, amounts and due date.
 *
 * Payment-first invoices (PAYMENT_APPROVAL) are created after the money is
 * received, so they are treated as PAID. Advance Tax Invoices are created
 * before payment, so they show the outstanding amount as due.
 */
public final class InvoiceDocumentSupport {

    private static final Logger log = LogManager.getLogger(InvoiceDocumentSupport.class);

    public static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private InvoiceDocumentSupport() {
    }

    // =====================================================
    // RELATED ENTITIES (unbilled first, then estimate)
    // =====================================================

    public static Estimate estimate(Invoice invoice) {
        if (invoice.getEstimate() != null) {
            return invoice.getEstimate();
        }
        UnbilledInvoice u = invoice.getUnbilledInvoice();
        return u != null ? u.getEstimate() : null;
    }

    public static Contact contact(Invoice invoice) {
        UnbilledInvoice u = invoice.getUnbilledInvoice();
        if (u != null && u.getContact() != null) {
            return u.getContact();
        }
        Estimate e = estimate(invoice);
        if (e != null && e.getContact() != null) {
            return e.getContact();
        }
        CompanyUnit unit = unit(invoice);
        return unit != null ? unit.getPrimaryContact() : null;
    }

    public static CompanyUnit unit(Invoice invoice) {
        UnbilledInvoice u = invoice.getUnbilledInvoice();
        if (u != null && u.getUnit() != null) {
            return u.getUnit();
        }
        Estimate e = estimate(invoice);
        return e != null ? e.getUnit() : null;
    }

    public static Company company(Invoice invoice) {
        UnbilledInvoice u = invoice.getUnbilledInvoice();
        if (u != null && u.getCompany() != null) {
            return u.getCompany();
        }
        Estimate e = estimate(invoice);
        return e != null ? e.getCompany() : null;
    }

    /** Salesperson who owns the client: estimate creator, then unbilled creator, then invoice creator. */
    public static User accountManager(Invoice invoice) {
        Estimate e = estimate(invoice);
        if (e != null && e.getCreatedBy() != null) {
            return e.getCreatedBy();
        }
        UnbilledInvoice u = invoice.getUnbilledInvoice();
        if (u != null && u.getCreatedBy() != null) {
            return u.getCreatedBy();
        }
        return invoice.getCreatedBy();
    }

    // =====================================================
    // GST / READINESS
    // =====================================================

    public static GstRegistrationType gstType(Invoice invoice) {
        return invoice.getEffectiveGstRegistrationType();
    }

    /**
     * REGISTERED / SEZ invoices may go to the client only after the e-invoice
     * (IRN + QR) is confirmed. UNREGISTERED / INTERNATIONAL can go immediately.
     */
    public static boolean isReadyToSend(Invoice invoice) {
        if (invoice.isCancelled()) {
            return false;
        }
        if (!gstType(invoice).requiresEInvoiceConfirmation()) {
            return true;
        }
        return invoice.getStatus() == InvoiceStatus.E_INVOICE_CONFIRMED
                || hasText(invoice.getEInvoiceIrn());
    }

    // =====================================================
    // AMOUNTS
    // =====================================================

    public static boolean isPaid(Invoice invoice) {
        if (invoice.isPaymentApprovalInvoice()) {
            return true;    // generated from an approved payment
        }
        if (invoice.getPaymentStatus() != null && "PAID".equals(invoice.getPaymentStatus().name())) {
            return true;
        }
        return amountDue(invoice).signum() <= 0;
    }

    /** Amount the client still has to pay (0 for paid invoices). */
    public static BigDecimal amountDue(Invoice invoice) {
        if (invoice.isPaymentApprovalInvoice()) {
            return BigDecimal.ZERO;
        }
        BigDecimal due = invoice.getAvailableOutstandingAmount();
        return due != null ? due.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    public static LocalDate dueDate(Invoice invoice, int dueDays) {
        LocalDate base = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now();
        return base.plusDays(Math.max(dueDays, 0));
    }

    /** Whole rupees when there are no paise (₹35,400), otherwise with paise (₹35,400.50). */
    public static String amount(BigDecimal value, String currency) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        boolean hasPaise = v.remainder(BigDecimal.ONE).signum() != 0;
        return EstimateEmailComposer.money(v, currency, hasPaise);
    }

    public static String amountNote(Invoice invoice) {
        return invoice.isZeroRatedSupply() ? "(zero-rated, no GST)" : "(inclusive of GST)";
    }

    // =====================================================
    // FORMATTING
    // =====================================================

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "-";
    }

    public static String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FMT) : null;
    }

    /** Invoice_CS-24-25-0012.pdf (slashes etc. are not allowed in file names) */
    public static String fileName(Invoice invoice) {
        String no = invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : String.valueOf(invoice.getId());
        return "Invoice_" + no.replaceAll("[\\\\/:*?\"<>|\\s]+", "-") + ".pdf";
    }

    public static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // =====================================================
    // PLACE OF SUPPLY
    // =====================================================

    private static final Map<String, String> GST_STATES = Map.ofEntries(
            Map.entry("01", "Jammu and Kashmir"), Map.entry("02", "Himachal Pradesh"),
            Map.entry("03", "Punjab"), Map.entry("04", "Chandigarh"),
            Map.entry("05", "Uttarakhand"), Map.entry("06", "Haryana"),
            Map.entry("07", "Delhi"), Map.entry("08", "Rajasthan"),
            Map.entry("09", "Uttar Pradesh"), Map.entry("10", "Bihar"),
            Map.entry("11", "Sikkim"), Map.entry("12", "Arunachal Pradesh"),
            Map.entry("13", "Nagaland"), Map.entry("14", "Manipur"),
            Map.entry("15", "Mizoram"), Map.entry("16", "Tripura"),
            Map.entry("17", "Meghalaya"), Map.entry("18", "Assam"),
            Map.entry("19", "West Bengal"), Map.entry("20", "Jharkhand"),
            Map.entry("21", "Odisha"), Map.entry("22", "Chhattisgarh"),
            Map.entry("23", "Madhya Pradesh"), Map.entry("24", "Gujarat"),
            Map.entry("26", "Dadra and Nagar Haveli and Daman and Diu"),
            Map.entry("27", "Maharashtra"), Map.entry("29", "Karnataka"),
            Map.entry("30", "Goa"), Map.entry("31", "Lakshadweep"),
            Map.entry("32", "Kerala"), Map.entry("33", "Tamil Nadu"),
            Map.entry("34", "Puducherry"), Map.entry("35", "Andaman and Nicobar Islands"),
            Map.entry("36", "Telangana"), Map.entry("37", "Andhra Pradesh"),
            Map.entry("38", "Ladakh"), Map.entry("96", "Other Country"),
            Map.entry("97", "Other Territory")
    );

    public static String placeOfSupply(Invoice invoice) {
        String code = invoice.getPlaceOfSupplyStateCode();
        if (!hasText(code)) {
            return gstType(invoice) == GstRegistrationType.INTERNATIONAL ? "Outside India" : null;
        }
        String c = code.trim();
        if (c.length() == 1) {
            c = "0" + c;
        }
        String name = GST_STATES.get(c);
        return name != null ? name + " (" + c + ")" : c;
    }

    // =====================================================
    // AMOUNT IN WORDS (Indian system: lakh / crore)
    // =====================================================

    private static final String[] ONES = {
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
            "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    public static String amountInWords(BigDecimal amount, String currency) {
        BigDecimal v = (amount == null ? BigDecimal.ZERO : amount).abs().setScale(2, RoundingMode.HALF_UP);
        long whole = v.longValue();
        int fraction = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        boolean inr = currency == null || "INR".equalsIgnoreCase(currency);
        StringBuilder sb = new StringBuilder(inr ? "Indian Rupees " : currency.toUpperCase(Locale.ROOT) + " ");
        sb.append(numberToWords(whole));
        if (fraction > 0) {
            sb.append(" and ").append(numberToWords(fraction)).append(inr ? " Paise" : " Cents");
        }
        return sb.append(" Only").toString();
    }

    private static String numberToWords(long n) {
        if (n == 0) {
            return "Zero";
        }
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1_000;
        n %= 1_000;

        if (crore > 0) {
            sb.append(numberToWords(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(belowHundred((int) lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(belowHundred((int) thousand)).append(" Thousand ");
        }
        if (n > 0) {
            sb.append(belowThousand((int) n));
        }
        return sb.toString().trim();
    }

    private static String belowThousand(int n) {
        if (n < 100) {
            return belowHundred(n);
        }
        String rest = n % 100 == 0 ? "" : " " + belowHundred(n % 100);
        return ONES[n / 100] + " Hundred" + rest;
    }

    private static String belowHundred(int n) {
        if (n < 20) {
            return ONES[n];
        }
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }

    // =====================================================
    // E-INVOICE QR CODE (signed QR from the IRP, as a PNG data URI)
    // =====================================================

    public static String qrCodeDataUri(String content, int sizePx) {
        if (!hasText(content)) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L); // signed QR is long
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new QRCodeWriter().encode(content.trim(), BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.warn("Could not render e-invoice QR code: {}", e.getMessage());
            return null;
        }
    }
}
