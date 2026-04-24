package com.waad.tba.modules.member.service;

import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.service.search.BeneficiarySearchStrategy;
import com.waad.tba.modules.member.service.search.BeneficiarySearchType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeneficiarySearchService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

    private final List<BeneficiarySearchStrategy> strategies;
    private final UnifiedMemberMapper unifiedMemberMapper;

    public List<MemberViewDto> search(
            BeneficiarySearchType type,
            String value,
            Long employerId,
            String status,
            Integer size) {

        String normalizedValue = validateAndNormalizeValue(type, value);
        Member.MemberStatus normalizedStatus = normalizeStatus(status);
        int normalizedSize = normalizeSize(size);

        Map<BeneficiarySearchType, BeneficiarySearchStrategy> strategyMap = new EnumMap<>(BeneficiarySearchType.class);
        for (BeneficiarySearchStrategy strategy : strategies) {
            strategyMap.put(strategy.supportedType(), strategy);
        }

        BeneficiarySearchStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported beneficiary search type: " + type);
        }

        return strategy.search(normalizedValue, employerId, normalizedStatus, normalizedSize)
                .stream()
                .map(unifiedMemberMapper::toViewDto)
                .toList();
    }

    private String validateAndNormalizeValue(BeneficiarySearchType type, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "type and value are required");
        }

        String normalized = value.trim();

        switch (type) {
            case BY_ID -> {
                if (!DIGITS_ONLY.matcher(normalized).matches()) {
                    throw new ResponseStatusException(BAD_REQUEST, "BY_ID requires digits only");
                }
            }
            case BY_BARCODE -> {
                if (!BARCODE_PATTERN.matcher(normalized).matches()) {
                    throw new ResponseStatusException(BAD_REQUEST, "BY_BARCODE accepts letters, digits, and '-' only");
                }
            }
            case BY_NAME -> {
                // Accept any non-blank text including names that start with digits.
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported beneficiary search type: " + type);
        }

        return normalized;
    }

    private Member.MemberStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return Member.MemberStatus.ACTIVE;
        }

        try {
            return Member.MemberStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid status value: " + status);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size <= 0) {
            return 1;
        }
        return Math.min(size, MAX_SIZE);
    }
}
