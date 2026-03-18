package com.waad.tba.modules.employer.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Employer Entity - The single top-level business entity in the system.
 * All members, benefit policies, provider contracts belong to an employer.
 */
@Entity
@Table(name = "employers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Employer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Employer code is required")
    @Column(nullable = false, unique = true)
    private String code;

    @NotBlank(message = "Employer name is required")
    @Column(nullable = false, name = "name")
    private String name;

    private String address;

    private String phone;

    @Email(message = "Email must be valid")
    private String email;

    private String logoUrl;

    private String businessType;

    private String website;

    /** رقم السجل التجاري */
    @Column(name = "cr_number")
    private String crNumber;

    /** الرقم الضريبي */
    @Column(name = "tax_number")
    private String taxNumber;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    /** NULL means unlimited */
    @Column(name = "max_member_limit")
    private Integer maxMemberLimit;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean isDefault = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
