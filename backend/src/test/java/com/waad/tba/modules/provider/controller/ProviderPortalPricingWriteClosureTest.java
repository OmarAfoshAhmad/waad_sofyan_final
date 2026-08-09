package com.waad.tba.modules.provider.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPortalPricingWriteClosureTest {

    @Test
    void directContractPricingWriteIsClosedForEveryRole() {
        Method endpoint = java.util.Arrays.stream(ProviderPortalController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("addMyContractPricing"))
                .findFirst()
                .orElseThrow();

        PreAuthorize authorization = endpoint.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("denyAll()");
    }
}
