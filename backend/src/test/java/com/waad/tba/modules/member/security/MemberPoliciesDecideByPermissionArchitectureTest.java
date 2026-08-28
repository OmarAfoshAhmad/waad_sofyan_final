package com.waad.tba.modules.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A member access policy may not ask what an account is called.
 *
 * The rule it enforces has to be the one an administrator can grant and a
 * screen can read. When it was a ladder of role names, neither was true: the
 * permission catalogue offered MEMBER_CHANGE_STATUS, the endpoint checked it,
 * the UI drew a button from it, and then the policy ignored all three and
 * asked whether the account was an EMPLOYER_ADMIN. A granted permission
 * produced a visible button and a 403.
 *
 * It is a comfortable thing to reintroduce, because a role check reads as
 * obviously correct at the line where it sits. It only looks wrong from the
 * screen, which is where nobody is looking when they write it.
 *
 * Scope resolution is a different question and is deliberately not covered:
 * which employers an account reaches genuinely does depend on its shape, and
 * MemberAccessScopeResolver reads roles for that reason.
 */
class MemberPoliciesDecideByPermissionArchitectureTest {

    /** The files that answer "may this user perform this operation". */
    private static final List<String> DECISION_POLICIES = List.of(
            "MemberQueryAccessPolicy.java",
            "MemberCommandAccessPolicy.java",
            "MemberImportAccessPolicy.java");

    private static final Pattern ROLE_CHECK = Pattern.compile(
            "\\b(isSuperAdmin|isEmployerAdmin|isDataEntry|isProvider|isMedicalReviewer|isAccountant)\\s*\\(");

    private static final Path POLICY_DIR =
            Paths.get("src/main/java/com/waad/tba/modules/member/security");

    /**
     * The one role reading left standing, and why. AuthorizedMemberScope
     * carries maskSensitiveFields so a provider does not receive a national
     * number it has no use for. That shapes the response; it does not decide
     * whether the operation is allowed.
     */
    private static final String PERMITTED_ROLE_READ = "isProvider(user));";

    @Test
    @DisplayName("no member access policy decides an operation by role name")
    void noPolicyDecidesByRoleName() throws IOException {
        List<String> offences = new ArrayList<>();

        for (String fileName : DECISION_POLICIES) {
            Path file = POLICY_DIR.resolve(fileName);
            if (!Files.exists(file)) {
                continue;
            }
            String source = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
            for (String line : source.split("\n")) {
                if (line.contains(PERMITTED_ROLE_READ)) {
                    continue;
                }
                Matcher matcher = ROLE_CHECK.matcher(line);
                if (matcher.find()) {
                    offences.add(fileName + " -> " + line.trim());
                }
            }
        }

        assertThat(offences)
                .as("the permission decides whether an operation is allowed, the scope "
                        + "decides on whose members, and domain rules decide whether the "
                        + "record's state permits it. A role is a default set of "
                        + "permissions -- if it is also a gate, an administrator cannot "
                        + "grant an exception and the UI cannot tell what the server will "
                        + "do. Use MemberOperationPermissions instead.")
                .isEmpty();
    }

    @Test
    @DisplayName("every member operation has a permission decision recorded against it")
    void everyOperationHasADecision() {
        List<String> undecided = new ArrayList<>();
        for (MemberOperation operation : MemberOperation.values()) {
            try {
                MemberOperationPermissions.requiredFor(operation);
            } catch (IllegalStateException missing) {
                undecided.add(operation.name());
            }
        }

        assertThat(undecided)
                .as("an operation missing from the map is one whose gate someone forgot, "
                        + "and the absence is indistinguishable from a deliberate 'needs "
                        + "no grant'. Adding an operation must force the decision.")
                .isEmpty();
    }

    /** Strips comments and string literals so prose describing the rule cannot trip it. */
    private static String codeOnly(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder();
        for (String line : withoutBlockComments.split("\n", -1)) {
            int comment = line.indexOf("//");
            out.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return out.toString().replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}
