// com.account.dto.company.request.ApproveRejectUnitRequestDto.java
package com.account.dto.company.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveRejectUnitRequestDto {
    @NotNull(message = "Approve flag is required")
    private Boolean approve;

    private String remark; // required only when approve = false
}