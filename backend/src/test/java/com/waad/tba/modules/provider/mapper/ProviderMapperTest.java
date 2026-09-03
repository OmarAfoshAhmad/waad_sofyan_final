package com.waad.tba.modules.provider.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.provider.entity.Provider;

/**
 * toViewDto() used to omit allowAllEmployers entirely, so every provider
 * read back through the API as "not allowed" regardless of what was
 * actually stored -- an import that correctly set it to true, or a
 * provider explicitly saved with it true, both looked unset in the edit
 * screen, forcing a manual re-toggle that changed nothing real.
 */
class ProviderMapperTest {

    private final ProviderMapper mapper = new ProviderMapper();

    @Test
    void reportsAllowAllEmployersAsStoredOnTheEntity() {
        Provider allowed = Provider.builder().id(1L).name("مستشفى الاختبار").allowAllEmployers(true).build();
        Provider restricted = Provider.builder().id(2L).name("عيادة الاختبار").allowAllEmployers(false).build();

        assertThat(mapper.toViewDto(allowed).getAllowAllEmployers()).isTrue();
        assertThat(mapper.toViewDto(restricted).getAllowAllEmployers()).isFalse();
    }
}
