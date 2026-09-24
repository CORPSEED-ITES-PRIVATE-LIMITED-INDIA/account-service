package com.account.dto.ledger;

import com.account.domain.ledger.LedgerGroupType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LedgerGroupRequestDto {

    @NotBlank(message = "Ledger group name is required")
    private String name;

    @NotNull(message = "Ledger group type is required")
    private LedgerGroupType groupType;

    private String description;

    private Boolean systemDefault = false;

    private Boolean active = true;
}