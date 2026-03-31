package com.account.dto.unbilled;


import lombok.Data;

@Data
public class UnbilledInvoiceSearchRequest {

    private String unbilledNumber;
    private String companyName;
    private int page = 1;   // default 1 (frontend friendly)
    private int size = 10;
}
