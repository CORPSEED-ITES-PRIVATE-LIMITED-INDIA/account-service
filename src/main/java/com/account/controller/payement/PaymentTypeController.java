package com.account.controller.payement;

import com.account.dto.payment.PaymentTypeRequestDto;
import com.account.dto.payment.PaymentTypeResponseDto;
import com.account.service.PaymentTypeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-types")
@Validated
public class PaymentTypeController {

    @Autowired
    private PaymentTypeService paymentTypeService;

    @PostMapping
    public ResponseEntity<PaymentTypeResponseDto> create(@Valid @RequestBody PaymentTypeRequestDto dto) {
        return new ResponseEntity<>(paymentTypeService.createPaymentType(dto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentTypeResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentTypeService.getPaymentTypeById(id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentTypeResponseDto>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(paymentTypeService.getAllPaymentTypes(page, size));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentTypeResponseDto> update(
            @PathVariable Long id,
            @Valid @RequestBody PaymentTypeRequestDto dto) {
        return ResponseEntity.ok(paymentTypeService.updatePaymentType(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentTypeService.deletePaymentType(id);
        return ResponseEntity.noContent().build();
    }
}