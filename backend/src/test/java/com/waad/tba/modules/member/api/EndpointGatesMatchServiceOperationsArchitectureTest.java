package com.waad.tba.modules.member.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An endpoint's @PreAuthorize has to name the permission its service will
 * actually demand.
 *
 * Nothing checked that, and two endpoints had drifted apart in the two ways
 * it can happen.
 *
 * PUT /{id}/reinstate declared MEMBER_REINSTATE_TERMINATED while the service
 * asked the policy for MemberOperation.REINSTATE -- MEMBER_CHANGE_STATUS --
 * so the dedicated grant that exists precisely to be handed to one person got
 * them past the annotation and refused by the service. The effective rule was
 * "both permissions", which neither layer said.
 *
 * GET /duplicates declared SYSTEM_SETTINGS_VIEW while findDuplicates()
 * required DANGER_ZONE_EXECUTE, so the gate a reader saw was not the gate
 * that applied.
 *
 * Neither is a hole -- the service refuses either way. Both make the
 * annotation a lie, and an annotation nobody can trust is worse than none:
 * it is what the front end reads to decide what to show, and what a reviewer
 * reads to decide whether an endpoint is safe.
 *
 * The rule asserted is one-directional. An endpoint may require MORE than its
 * service does; it may not require less than, or something other than, what
 * the service enforces.
 */
class EndpointGatesMatchServiceOperationsArchitectureTest {

    private static final Path MODULE = Path.of("src/main/java/com/waad/tba/modules/member");

    private static final Pattern PRE_AUTHORIZE = Pattern.compile(
            "@PreAuthorize\\(\"([^\"]+)\"\\)(.*?)(?=\\n\\s*@(?:Get|Post|Put|Patch|Delete)Mapping|\\Z)",
            Pattern.DOTALL);
    private static final Pattern HAS_PERMISSION = Pattern.compile("has\\('(\\w+)'\\)");
    private static final Pattern SERVICE_CALL = Pattern.compile("\\b\\w*[Ss]ervice\\.(\\w+)\\s*\\(");
    private static final Pattern OPERATION_USE = Pattern.compile("MemberOperation\\.(\\w+)");
    private static final Pattern METHOD_START =
            Pattern.compile("\\n    (?:public|protected)\\s[^\\n(]*?\\b(\\w+)\\s*\\(");
    private static final Pattern MAP_ENTRY = Pattern.compile(
            "MemberOperation\\.(\\w+),\\s*(?:SystemPermission\\.(\\w+)|REACH_IS_ENOUGH)");

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /** operation to permission, absent where reaching the member is the whole question. */
    private Map<String, String> operationPermissions() throws IOException {
        String code = codeOnly(Files.readString(
                MODULE.resolve("security/MemberOperationPermissions.java"), StandardCharsets.UTF_8));
        Map<String, String> byOperation = new HashMap<>();
        Matcher m = MAP_ENTRY.matcher(code);
        while (m.find()) {
            if (m.group(2) != null) {
                byOperation.put(m.group(1), m.group(2));
            }
        }
        return byOperation;
    }

    /** service method name to the operations its body requires. */
    private Map<String, Set<String>> operationsRequiredByServiceMethod() throws IOException {
        Map<String, Set<String>> byMethod = new HashMap<>();
        try (Stream<Path> services = Files.list(MODULE.resolve("service"))) {
            for (Path service : services.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(service, StandardCharsets.UTF_8);
                List<String> names = new ArrayList<>();
                List<int[]> bounds = new ArrayList<>();
                Matcher starts = METHOD_START.matcher(source);
                while (starts.find()) {
                    if (!bounds.isEmpty()) {
                        bounds.get(bounds.size() - 1)[1] = starts.start();
                    }
                    names.add(starts.group(1));
                    bounds.add(new int[] { starts.end(), source.length() });
                }
                for (int i = 0; i < names.size(); i++) {
                    String body = codeOnly(source.substring(bounds.get(i)[0], bounds.get(i)[1]));
                    Matcher ops = OPERATION_USE.matcher(body);
                    while (ops.find()) {
                        byMethod.computeIfAbsent(names.get(i), k -> new HashSet<>()).add(ops.group(1));
                    }
                }
            }
        }
        return byMethod;
    }

    @Test
    @DisplayName("every endpoint declares the permission its service enforces")
    void endpointGatesAreNotWeakerThanTheServiceBehindThem() throws IOException {
        Map<String, String> permissionOf = operationPermissions();
        Map<String, Set<String>> requiredBy = operationsRequiredByServiceMethod();

        assertThat(permissionOf).as("the operation map should not read as empty").isNotEmpty();
        assertThat(requiredBy).as("no service appears to require any operation -- the parser has drifted")
                .isNotEmpty();

        List<String> mismatches = new ArrayList<>();
        int paired = 0;

        try (Stream<Path> controllers = Files.list(MODULE.resolve("controller"))) {
            for (Path controller : controllers.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(controller, StandardCharsets.UTF_8);
                Matcher endpoints = PRE_AUTHORIZE.matcher(source);
                while (endpoints.find()) {
                    Set<String> declared = new HashSet<>();
                    Matcher perms = HAS_PERMISSION.matcher(endpoints.group(1));
                    while (perms.find()) {
                        declared.add(perms.group(1));
                    }

                    String body = codeOnly(endpoints.group(2));
                    Matcher calls = SERVICE_CALL.matcher(body);
                    Set<String> operations = new HashSet<>();
                    while (calls.find()) {
                        operations.addAll(requiredBy.getOrDefault(calls.group(1), Set.of()));
                    }
                    if (operations.isEmpty()) {
                        // Nothing resolvable here: a template download, or a
                        // call this parser cannot follow. Not asserted -- and
                        // the paired count below makes silent total failure
                        // impossible.
                        continue;
                    }
                    paired++;

                    for (String operation : operations) {
                        String needed = permissionOf.get(operation);
                        if (needed != null && !declared.contains(needed)) {
                            mismatches.add(controller.getFileName() + " -> " + operation
                                    + ": endpoint declares " + declared
                                    + " but the service enforces " + needed);
                        }
                    }
                }
            }
        }

        assertThat(paired)
                .as("if nothing pairs up, this passes on nothing -- the parsers have drifted from how "
                        + "controllers call services or how services ask the policy")
                .isGreaterThanOrEqualTo(25);

        assertThat(mismatches)
                .as("the annotation is what the front end reads to decide what to show, and what a "
                        + "reviewer reads to decide an endpoint is safe; it has to name the gate that applies")
                .isEmpty();
    }

    @Test
    @DisplayName("no member endpoint is left with no gate at all")
    void everyMemberEndpointIsGuarded() throws IOException {
        Pattern mapping = Pattern.compile("@(?:Get|Post|Put|Patch|Delete)Mapping");
        List<String> unguarded = new ArrayList<>();

        try (Stream<Path> controllers = Files.list(MODULE.resolve("controller"))) {
            for (Path controller : controllers.filter(p -> p.toString().endsWith(".java")).toList()) {
                String[] lines = codeOnly(Files.readString(controller, StandardCharsets.UTF_8)).split("\\R");
                for (int i = 0; i < lines.length; i++) {
                    if (!mapping.matcher(lines[i]).find()) {
                        continue;
                    }
                    boolean guarded = false;
                    for (int j = Math.max(0, i - 4); j < Math.min(lines.length, i + 6); j++) {
                        if (lines[j].contains("@PreAuthorize")) {
                            guarded = true;
                            break;
                        }
                    }
                    if (!guarded) {
                        unguarded.add(controller.getFileName() + ": " + lines[i].trim());
                    }
                }
            }
        }

        assertThat(unguarded)
                .as("an endpoint with no @PreAuthorize is reachable by any authenticated user; whatever "
                        + "the service does about it, that is not a decision anyone made here")
                .isEmpty();
    }
}
