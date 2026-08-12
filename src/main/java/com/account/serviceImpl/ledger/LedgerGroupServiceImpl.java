package com.account.serviceImpl.ledger;

import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import com.account.dto.ledger.LedgerGroupRequestDto;
import com.account.dto.ledger.LedgerGroupResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.service.ledger.LedgerGroupService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LedgerGroupServiceImpl implements LedgerGroupService {

    private final LedgerGroupRepository ledgerGroupRepository;

    @Override
    @Transactional
    public LedgerGroupResponseDto createLedgerGroup(LedgerGroupRequestDto request) {

        validateRequest(request);

        String normalizedName = normalizeName(request.getName());

        if (ledgerGroupRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ValidationException(
                    "Ledger group already exists with name: " + normalizedName,
                    "ERR_LEDGER_GROUP_DUPLICATE",
                    "name"
            );
        }

        if (ledgerGroupRepository.existsByGroupType(request.getGroupType())) {
            throw new ValidationException(
                    "Ledger group already exists for type: " + request.getGroupType(),
                    "ERR_LEDGER_GROUP_TYPE_DUPLICATE",
                    "groupType"
            );
        }

        LedgerGroup ledgerGroup = LedgerGroup.builder()
                .name(normalizedName)
                .groupType(request.getGroupType())
                .description(clean(request.getDescription()))
                .systemDefault(Boolean.TRUE.equals(request.getSystemDefault()))
                .active(request.getActive() == null || request.getActive())
                .deleted(false)
                .build();

        LedgerGroup saved = ledgerGroupRepository.save(ledgerGroup);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public LedgerGroupResponseDto updateLedgerGroup(Long id, LedgerGroupRequestDto request) {

        validateRequest(request);

        LedgerGroup ledgerGroup = ledgerGroupRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger group not found with ID: " + id,
                        "LEDGER_GROUP_NOT_FOUND"
                ));

        String normalizedName = normalizeName(request.getName());

        if (ledgerGroup.isSystemDefault()) {
            if (request.getGroupType() != ledgerGroup.getGroupType()) {
                throw new ValidationException(
                        "System default ledger group type cannot be changed",
                        "ERR_SYSTEM_LEDGER_GROUP_TYPE_EDIT_NOT_ALLOWED",
                        "groupType"
                );
            }

            if (Boolean.FALSE.equals(request.getSystemDefault())) {
                throw new ValidationException(
                        "System default flag cannot be removed from a system ledger group",
                        "ERR_SYSTEM_LEDGER_GROUP_FLAG_EDIT_NOT_ALLOWED",
                        "systemDefault"
                );
            }

            if (Boolean.FALSE.equals(request.getActive())) {
                throw new ValidationException(
                        "System default ledger group cannot be deactivated",
                        "ERR_SYSTEM_LEDGER_GROUP_DEACTIVATE_NOT_ALLOWED",
                        "active"
                );
            }
        }

        if (ledgerGroupRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new ValidationException(
                    "Ledger group already exists with name: " + normalizedName,
                    "ERR_LEDGER_GROUP_DUPLICATE",
                    "name"
            );
        }

        if (ledgerGroupRepository.existsByGroupTypeAndIdNot(request.getGroupType(), id)) {
            throw new ValidationException(
                    "Another ledger group already exists for type: " + request.getGroupType(),
                    "ERR_LEDGER_GROUP_TYPE_DUPLICATE",
                    "groupType"
            );
        }

        ledgerGroup.setName(normalizedName);
        ledgerGroup.setGroupType(request.getGroupType());
        ledgerGroup.setDescription(clean(request.getDescription()));

        if (ledgerGroup.isSystemDefault()) {
            ledgerGroup.setSystemDefault(true);
            ledgerGroup.setActive(true);
        } else {
            if (request.getSystemDefault() != null) {
                ledgerGroup.setSystemDefault(request.getSystemDefault());
            }

            if (request.getActive() != null) {
                ledgerGroup.setActive(request.getActive());
            }
        }

        LedgerGroup saved = ledgerGroupRepository.save(ledgerGroup);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerGroupResponseDto getLedgerGroupById(Long id) {

        LedgerGroup ledgerGroup = ledgerGroupRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger group not found with ID: " + id,
                        "LEDGER_GROUP_NOT_FOUND"
                ));

        return mapToResponse(ledgerGroup);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerGroupResponseDto> getLedgerGroups(
            String search,
            LedgerGroupType groupType,
            Boolean active,
            int page,
            int size
    ) {

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 || size > 200 ? 20 : size;

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.ASC, "name")
        );

        Specification<LedgerGroup> specification = buildSpecification(search, groupType, active);

        return ledgerGroupRepository.findAll(specification, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerGroupResponseDto> getActiveLedgerGroups() {

        return ledgerGroupRepository.findByDeletedFalseAndActiveTrueOrderByNameAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteLedgerGroup(Long id) {

        LedgerGroup ledgerGroup = ledgerGroupRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger group not found with ID: " + id,
                        "LEDGER_GROUP_NOT_FOUND"
                ));

        if (ledgerGroup.isSystemDefault()) {
            throw new ValidationException(
                    "System default ledger group cannot be deleted",
                    "ERR_SYSTEM_LEDGER_GROUP_DELETE_NOT_ALLOWED",
                    "id"
            );
        }

        ledgerGroup.setDeleted(true);
        ledgerGroup.setActive(false);

        ledgerGroupRepository.save(ledgerGroup);
    }

    private Specification<LedgerGroup> buildSpecification(
            String search,
            LedgerGroupType groupType,
            Boolean active
    ) {
        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));

            if (search != null && !search.trim().isEmpty()) {
                String likeSearch = "%" + search.trim().toLowerCase() + "%";

                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("name")),
                                likeSearch
                        )
                );
            }

            if (groupType != null) {
                predicates.add(criteriaBuilder.equal(root.get("groupType"), groupType));
            }

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void validateRequest(LedgerGroupRequestDto request) {

        if (request == null) {
            throw new ValidationException(
                    "Request body is required",
                    "ERR_REQUEST_REQUIRED"
            );
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException(
                    "Ledger group name is required",
                    "ERR_LEDGER_GROUP_NAME_REQUIRED",
                    "name"
            );
        }

        if (request.getGroupType() == null) {
            throw new ValidationException(
                    "Ledger group type is required",
                    "ERR_LEDGER_GROUP_TYPE_REQUIRED",
                    "groupType"
            );
        }
    }

    private String normalizeName(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private LedgerGroupResponseDto mapToResponse(LedgerGroup ledgerGroup) {

        if (ledgerGroup == null) {
            return null;
        }

        return LedgerGroupResponseDto.builder()
                .id(ledgerGroup.getId())
                .name(ledgerGroup.getName())
                .groupType(ledgerGroup.getGroupType())
                .description(ledgerGroup.getDescription())
                .systemDefault(ledgerGroup.isSystemDefault())
                .active(ledgerGroup.isActive())
                .deleted(ledgerGroup.isDeleted())
                .createdAt(ledgerGroup.getCreatedAt())
                .updatedAt(ledgerGroup.getUpdatedAt())
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getLedgerGroupTypeByGroupId(Long groupId) {

        if (groupId == null || groupId <= 0) {
            throw new ValidationException(
                    "Valid ledger group ID is required",
                    "ERR_LEDGER_GROUP_ID_REQUIRED",
                    "groupId"
            );
        }

        LedgerGroup ledgerGroup = ledgerGroupRepository
                .findByIdAndDeletedFalse(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger group not found with ID: " + groupId,
                        "LEDGER_GROUP_NOT_FOUND"
                ));

        LedgerGroupType groupType = ledgerGroup.getGroupType();

        return Map.of(
                "groupId", ledgerGroup.getId(),
                "groupName", ledgerGroup.getName(),
                "groupType", groupType.name(),
                "groupTypeLabel", formatGroupTypeLabel(groupType)
        );
    }

    private String formatGroupTypeLabel(LedgerGroupType groupType) {

        if (groupType == null) {
            return null;
        }

        return Arrays.stream(
                        groupType.name()
                                .toLowerCase()
                                .split("_")
                )
                .map(word ->
                        word.substring(0, 1).toUpperCase()
                                + word.substring(1)
                )
                .reduce((first, second) -> first + " " + second)
                .orElse(groupType.name());
    }



}