package com.waad.tba.modules.systemadmin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for self-service profile update.
 * All fields are optional — only non-null values will be applied.
 */
@Data
public class ProfileUpdateRequest {

    @Size(min = 2, max = 100, message = "الاسم الكامل يجب أن يكون بين 2 و 100 حرف")
    private String fullName;

    @Email(message = "صيغة البريد الإلكتروني غير صحيحة")
    @Size(max = 150, message = "البريد الإلكتروني طويل جداً")
    private String email;

    @Pattern(regexp = "^\\+?[\\d\\s\\-\\(\\)]{7,20}$", message = "صيغة رقم الهاتف غير صحيحة")
    @Size(max = 30, message = "رقم الهاتف طويل جداً")
    private String phone;
}
