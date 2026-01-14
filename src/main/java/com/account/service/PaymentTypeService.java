package com.account.service;

import com.account.dto.payment.PaymentTypeRequestDto;
import com.account.dto.payment.PaymentTypeResponseDto;

import java.util.List;

public interface PaymentTypeService {

    PaymentTypeResponseDto createPaymentType(PaymentTypeRequestDto dto);

    PaymentTypeResponseDto getPaymentTypeById(Long id);

    List<PaymentTypeResponseDto> getAllPaymentTypes(int page, int size);

    PaymentTypeResponseDto updatePaymentType(Long id, PaymentTypeRequestDto dto);

    void deletePaymentType(Long id);
}