package com.account.serviceImpl;

import com.account.domain.vendor.Vendor;
import com.account.dto.vendor.VendorResponseDto;
import com.account.dto.vendor.VendorSyncRequestDto;
import com.account.repository.VendorRepository;
import com.account.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;

    @Override
    @Transactional
    public VendorResponseDto syncVendor(VendorSyncRequestDto request) {

        Vendor vendor = vendorRepository
                .findByOperationVendorId(request.getOperationVendorId())
                .orElseGet(() -> Vendor.builder()
                        .operationVendorId(request.getOperationVendorId())
                        .build());

        validateIncomingVersion(vendor, request);

        vendor.setVendorName(request.getVendorName());
        vendor.setEmail(request.getEmail());
        vendor.setMobile(request.getMobile());
        vendor.setPan(request.getPan());
        vendor.setGstNumber(request.getGstNumber());
        vendor.setGstRegistrationType(request.getGstRegistrationType());
        vendor.setAccountHolderName(request.getAccountHolderName());
        vendor.setBankAccountNumber(request.getBankAccountNumber());
        vendor.setBankName(request.getBankName());
        vendor.setIfscCode(request.getIfscCode());
        vendor.setActive(
                request.getActive() != null
                        ? request.getActive()
                        : Boolean.TRUE
        );
        vendor.setOperationUpdatedAt(request.getOperationUpdatedAt());

        Vendor savedVendor = vendorRepository.save(vendor);

        return mapToResponse(savedVendor);
    }

    private void validateIncomingVersion(
            Vendor existingVendor,
            VendorSyncRequestDto request
    ) {
        if (existingVendor.getId() == null) {
            return;
        }

        if (existingVendor.getOperationUpdatedAt() == null
                || request.getOperationUpdatedAt() == null) {
            return;
        }

        if (request.getOperationUpdatedAt()
                .isBefore(existingVendor.getOperationUpdatedAt())) {

            throw new IllegalArgumentException(
                    "Incoming vendor data is older than the existing vendor data"
            );
        }
    }

    private VendorResponseDto mapToResponse(Vendor vendor) {

        return VendorResponseDto.builder()
                .id(vendor.getId())
                .operationVendorId(vendor.getOperationVendorId())
                .vendorName(vendor.getVendorName())
                .email(vendor.getEmail())
                .mobile(vendor.getMobile())
                .pan(vendor.getPan())
                .gstNumber(vendor.getGstNumber())
                .gstRegistrationType(vendor.getGstRegistrationType())
                .accountHolderName(vendor.getAccountHolderName())
                .bankAccountNumber(vendor.getBankAccountNumber())
                .bankName(vendor.getBankName())
                .ifscCode(vendor.getIfscCode())
                .active(vendor.getActive())
                .operationUpdatedAt(vendor.getOperationUpdatedAt())
                .syncedAt(vendor.getSyncedAt())
                .build();
    }
}