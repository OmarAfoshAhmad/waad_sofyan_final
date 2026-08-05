package com.waad.tba.modules.employer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One parsed row of an employer bulk-import file, carrying both the raw sheet
 * values (for display in the preview/error report) and the resolved values
 * (populated only once structurally valid). Cached between the preview and
 * confirm steps via {@link com.waad.tba.modules.providercontract.service.PricingImportSessionCache}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployerImportRowDto implements Serializable {

    public enum Action {
        /** No existing employer matches this row — a new one will be created. */
        CREATE,
        /** An existing employer matches (by code or name) and at least one field differs. */
        UPDATE,
        /** An existing employer matches and every field is already identical — nothing to write. */
        NO_CHANGE
    }

    private int rowNumber;

    // Raw values as read from the sheet
    private String codeRaw;
    private String nameRaw;
    private String phoneRaw;
    private String emailRaw;
    private String addressRaw;
    private String annualLimitRaw;
    private String coveragePercentRaw;

    // Resolved values, populated only when structurally valid.
    // A blank cell resolves to null here — the row processor merges null fields
    // with the existing employer's current value rather than overwriting it,
    // so re-importing a file that only fills in some columns never erases data.
    private String code;
    private String name;
    private String phone;
    private String email;
    private String address;
    private BigDecimal annualLimit;

    /** Policy-level default coverage percentage (0-100). Null resolves to 100 when the policy is created. */
    private Integer coveragePercent;

    /** Set once matched against an existing employer (by code, else by name). Null means CREATE. */
    private Long existingEmployerId;

    private Action action;

    /** Which fields actually differ from the existing employer, for the preview UI. Empty for CREATE/NO_CHANGE. */
    @Builder.Default
    private List<String> changedFields = new ArrayList<>();

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }

    public void addError(String message) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(message);
    }
}
