package com.account.serviceImpl;

import com.account.domain.PaymentType;
import com.account.dto.payment.PaymentTypeRequestDto;
import com.account.dto.payment.PaymentTypeResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.PaymentTypeRepository;
import com.account.repository.UserRepository;
import com.account.service.PaymentTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PaymentTypeServiceImpl implements PaymentTypeService {

    @Autowired
    private PaymentTypeRepository paymentTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public PaymentTypeResponseDto createPaymentType(PaymentTypeRequestDto dto) {
        validateRequestForCreate(dto);

        if (paymentTypeRepository.existsByCodeAndIsDeletedFalse(dto.getCode())) {
            throw new ValidationException("Payment type code already exists", "DUPLICATE_CODE");
        }
        if (paymentTypeRepository.existsByNameAndIsDeletedFalse(dto.getName())) {
            throw new ValidationException("Payment type name already exists", "DUPLICATE_NAME");
        }

        assertUserExists(dto.getCreatedBy());
        assertUserExists(dto.getUpdatedBy());

        PaymentType entity = new PaymentType();
        entity.setCode(dto.getCode().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        entity.setDescription(dto.getDescription());
        entity.setActive(dto.getActive() != null ? dto.getActive() : true);
        entity.setCreatedBy(dto.getCreatedBy());
        entity.setUpdatedBy(dto.getUpdatedBy());
        entity.setUpdatedAt(LocalDateTime.now());

        entity = paymentTypeRepository.save(entity);

        return mapToResponseDto(entity);
    }

    @Override
    public PaymentTypeResponseDto getPaymentTypeById(Long id) {
        PaymentType entity = paymentTypeRepository.findById(id)
                .filter(pt -> !pt.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Payment type not found", "PAYMENT_TYPE_NOT_FOUND"));

        return mapToResponseDto(entity);
    }

    @Override
    public List<PaymentTypeResponseDto> getAllPaymentTypes(int page, int size) {
        var pageable = PageRequest.of(page, size);
        var pageResult = paymentTypeRepository.findByIsDeletedFalse(pageable);

        return pageResult.getContent().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentTypeResponseDto updatePaymentType(Long id, PaymentTypeRequestDto dto) {
        PaymentType entity = paymentTypeRepository.findById(id)
                .filter(pt -> !pt.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Payment type not found", "PAYMENT_TYPE_NOT_FOUND"));

        assertUserExists(dto.getUpdatedBy());

        // Check duplicates only if value changed
        if (!entity.getCode().equals(dto.getCode().trim().toUpperCase()) &&
                paymentTypeRepository.existsByCodeAndIsDeletedFalse(dto.getCode())) {
            throw new ValidationException("Code already in use", "DUPLICATE_CODE");
        }

        if (!entity.getName().equals(dto.getName().trim()) &&
                paymentTypeRepository.existsByNameAndIsDeletedFalse(dto.getName())) {
            throw new ValidationException("Name already in use", "DUPLICATE_NAME");
        }

        // Update fields
        entity.setCode(dto.getCode().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }
        entity.setUpdatedBy(dto.getUpdatedBy());
        entity.setUpdatedAt(LocalDateTime.now());

        entity = paymentTypeRepository.save(entity);
        return mapToResponseDto(entity);
    }

    @Override
    public void deletePaymentType(Long id) {
        int updated = paymentTypeRepository.softDeleteById(id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Payment type not found or already deleted", "PAYMENT_TYPE_NOT_FOUND");
        }
    }


    private void validateRequestForCreate(PaymentTypeRequestDto dto) {
        if (dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            throw new ValidationException("Code is required", "INVALID_CODE");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new ValidationException("Name is required", "INVALID_NAME");
        }
    }

    private void assertUserExists(Long userId) {
        if (!userRepository.existsByIdAndNotDeleted(userId)) {
            throw new ResourceNotFoundException("Active user not found: " + userId, "USER_NOT_FOUND");
        }
    }

    private PaymentTypeResponseDto mapToResponseDto(PaymentType entity) {
        PaymentTypeResponseDto dto = new PaymentTypeResponseDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.isActive());
        return dto;
    }
}