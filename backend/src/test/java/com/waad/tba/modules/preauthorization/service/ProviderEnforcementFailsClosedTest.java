package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * S-03 on the legitimate create path. validateAndEnforceProviderId used to
 * fail open in three separate ways at once:
 *
 *   1. a null principal returned early after a log line;
 *   2. canAccessInternalOperations -- which includes DATA_ENTRY -- was allowed
 *      to name any provider;
 *   3. a trailing "Other roles: no restriction on providerId" comment granted
 *      everyone else the same freedom by saying nothing at all.
 *
 * Only the controller's hasAnyRole list narrowed any of that, which made it a
 * trap for the RBAC migration in flight: widening the annotation would have
 * silently widened provider spoofing with it.
 *
 * A source scan, because what is being pinned is the absence of escape
 * hatches. A runtime test can show one path denies; it cannot show that no
 * unguarded branch was added back.
 */
class ProviderEnforcementFailsClosedTest {

    private String enforcementMethod() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/preauthorization/service/PreAuthorizationService.java"),
                StandardCharsets.UTF_8);
        int start = service.indexOf("private void validateAndEnforceProviderId");
        assertThat(start).as("validateAndEnforceProviderId must exist").isGreaterThan(-1);
        String body = service.substring(start, service.indexOf("\n    }", start));
        return body.lines()
                .map(line -> {
                    int comment = line.indexOf("//");
                    return comment < 0 ? line : line.substring(0, comment);
                })
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    @Test
    void aDeniedScopeStopsTheRequestRatherThanBeingLogged() throws Exception {
        String body = enforcementMethod();

        assertThat(body)
                .as("the scope decides, and a denied scope must throw")
                .contains("preAuthAccessScopeResolver.resolveFor(currentUser)")
                .contains("scope.isDenied()")
                .contains("throw new AccessDeniedException");
    }

    @Test
    void internalRolesNoLongerNameAnyProviderFreely() throws Exception {
        String body = enforcementMethod();

        assertThat(body)
                .as("canAccessInternalOperations must not be the gate: it includes "
                        + "DATA_ENTRY, which holds no PREAUTH permission in the V191 templates")
                .doesNotContain("canAccessInternalOperations");
    }

    @Test
    void aWiderScopeMustNameAProviderAndBeNarrowedToIt() throws Exception {
        String body = enforcementMethod();

        assertThat(body)
                .as("a caller with no single provider must name one explicitly")
                .contains("dto.getProviderId() == null")
                .contains("throw new BusinessRuleException");
        assertThat(body)
                .as("and the named provider must be re-checked against their scope")
                .contains("resolveFor(currentUser, dto.getProviderId())")
                .contains("narrowed.isDenied()");
    }

    @Test
    void aProviderIsStillForcedToFileAsItself() throws Exception {
        String body = enforcementMethod();

        assertThat(body)
                .as("the provider's own id overrides whatever the body carried")
                .contains("scope.singleProviderId()")
                .contains("dto.setProviderId(enforced)");
    }
}
