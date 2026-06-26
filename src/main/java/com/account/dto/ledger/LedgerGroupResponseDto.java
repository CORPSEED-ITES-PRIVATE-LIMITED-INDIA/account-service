package com.account.dto.ledger;

import com.account.domain.ledger.LedgerGroupType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class LedgerGroupResponseDto {

    private Long id;

    private String name;

    private LedgerGroupType groupType;

    private String description;

    private Boolean systemDefault;

    private Boolean active;

    private Boolean deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}