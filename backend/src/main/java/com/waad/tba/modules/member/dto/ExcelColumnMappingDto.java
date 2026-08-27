package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Excel Column Mapping DTO
 * 
 * User-confirmed mapping between Excel columns and Member fields
 * Used for actual import process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelColumnMappingDto {

    /**
     * Mapping of Excel column index to Member field name
     * 
     * Example:
     * {
     *   0: "nationalNumber",
     *   1: "fullName",
     *   2: "email",
     *   3: "phone",
     *   4: "dateOfBirth"
     * }
     */
    private Map<Integer, String> columnToFieldMapping;

    /**
     * Header row number (0-based)
     */
    private Integer headerRowNumber;

    /**
     * First data row number (0-based, typically 1)
     */
    private Integer firstDataRowNumber;

    /**
     * Whether to skip empty rows
     */
    private Boolean skipEmptyRows;

    /**
     * Whether to stop on first error or continue
     */
    private Boolean stopOnError;

    /**
     * Maximum number of rows to import (0 = all)
     */
    private Integer maxRows;
}
