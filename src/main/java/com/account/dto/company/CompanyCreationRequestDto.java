package com.account.dto.company;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CompanyCreationRequestDto {

    @NotNull(message = "companyId is required (manual ID)")
    private Long companyId;

    @NotBlank(message = "Company name is required")
    private String name;

    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN format")
    private String panNo;

    private LocalDate establishDate;

    private String industry;
    private String subIndustry;
    private String subsubIndustry;
    private String rating;

    private String paymentTerm;
    private boolean aggrementPresent;
    private String aggrement;
    private boolean ndaPresent;
    private String nda;
    private String revenue;

    private boolean isConsultant;

    @Valid
    private List<FullUnitCreationDto> units;

    // Audit / who is creating
    @NotNull(message = "createdById is required")
    private Long createdById;
}