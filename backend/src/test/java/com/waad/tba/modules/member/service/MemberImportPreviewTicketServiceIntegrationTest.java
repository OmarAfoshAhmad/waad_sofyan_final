package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberImportPreviewTicketServiceIntegrationTest extends PostgresIntegrationTestBase {
    @Autowired MemberImportPreviewTicketService tickets;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    User user;
    MockMultipartFile file;

    @BeforeEach void setup() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        user = users.save(User.builder().username("preview-" + s).password("x").fullName("Preview")
                .email("preview-" + s + "@test.local").userType("SUPER_ADMIN").active(true).build());
        authenticate(user);
        file = new MockMultipartFile("file", "members.xlsx", "application/octet-stream", new byte[]{1,2,3});
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void exactPreviewCanBeConsumedOnlyOnce() throws Exception {
        String token = tickets.issue(file, 10L, 20L, 2, false, Map.of("A", "cardNumber"), Set.of(10L));
        tickets.consume(token, file, 10L, 20L, 2, false, Map.of("A", "cardNumber"), Set.of(10L));
        assertThatThrownBy(() -> tickets.consume(token, file, 10L, 20L, 2, false,
                Map.of("A", "cardNumber"), Set.of(10L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("مسبقاً");
    }

    @Test void changedFileOptionsScopeOrUserAreRejected() throws Exception {
        String token = tickets.issue(file, 10L, null, null, false, Map.of(), Set.of(10L));
        var changed = new MockMultipartFile("file", "members.xlsx", "application/octet-stream", new byte[]{9});
        assertThatThrownBy(() -> tickets.consume(token, changed, 10L, null, null, false, Map.of(), Set.of(10L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("تغيّرت");
        assertThatThrownBy(() -> tickets.consume(token, file, 10L, null, null, true, Map.of(), Set.of(10L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("تغيّرت");
        assertThatThrownBy(() -> tickets.consume(token, file, 10L, null, null, false, Map.of(), Set.of(11L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("تغيّرت");
        User other = users.save(User.builder().username("other-" + UUID.randomUUID()).password("x")
                .fullName("Other").email(UUID.randomUUID() + "@test.local")
                .userType("SUPER_ADMIN").active(true).build());
        authenticate(other);
        assertThatThrownBy(() -> tickets.consume(token, file, 10L, null, null, false, Map.of(), Set.of(10L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("تغيّرت");
    }

    @Test void expiredPreviewFailsClosed() throws Exception {
        String token = tickets.issue(file, null, null, null, false, Map.of(), Set.of(10L));
        jdbc.update("""
                update member_import_preview_tickets
                   set created_at=current_timestamp - interval '1 hour',
                       expires_at=current_timestamp - interval '1 second'
                 where token=?::uuid
                """, token);
        assertThatThrownBy(() -> tickets.consume(token, file, null, null, null, false, Map.of(), Set.of(10L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("انتهت");
    }

    private void authenticate(User value) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(value.getUsername(), "x", List.of()));
    }
}
