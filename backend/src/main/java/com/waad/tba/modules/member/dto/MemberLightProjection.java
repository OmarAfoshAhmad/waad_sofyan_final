package com.waad.tba.modules.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.waad.tba.modules.member.entity.Member.Gender;
import com.waad.tba.modules.member.entity.Member.Relationship;

public interface MemberLightProjection {
    Long getId();
    String getFullName();
    String getCardNumber();
    String getNationalNumber();
    LocalDate getBirthDate();
    LocalDateTime getCreatedAt();
    Relationship getRelationship();
    Gender getGender();
    ParentLightProjection getParent();
    EmployerLightProjection getEmployer();
    
    interface ParentLightProjection {
        Long getId();
    }
    
    interface EmployerLightProjection {
        Long getId();
    }
}
