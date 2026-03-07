package com.waad.tba.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "Identifier is required")
    @Size(max = 150, message = "Identifier too long")
    private String identifier; // username or email

    @NotBlank(message = "Password is required")
    @Size(max = 200, message = "Password too long")
    private String password;
}
