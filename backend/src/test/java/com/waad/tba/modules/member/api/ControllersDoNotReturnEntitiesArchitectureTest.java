package com.waad.tba.modules.member.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A persistence entity must not be what an endpoint returns.
 *
 * GET /import/status/{batchId} did exactly that for as long as it existed. It
 * answered with MemberImportLog, so every column the table ever gained went
 * to the browser with it -- the file hash, the import scope hash, the
 * internal user id -- and the screen's contract was whatever the table
 * happened to look like that week. It also carried errorMessage, which held
 * a raw exception message, straight onto the progress widget.
 *
 * None of that was a decision. It is what returning an entity does, quietly,
 * every time the table changes. The endpoint is fixed; this exists so the
 * next one cannot be written the same way without someone saying so.
 *
 * The entity names are read from the entity package rather than listed here.
 * A list would have to be edited to stay correct, and a guard you must edit
 * to keep green is a guard that gets edited rather than satisfied -- a new
 * entity would simply not be covered.
 */
class ControllersDoNotReturnEntitiesArchitectureTest {

    private static final Path MODULE = Path.of("src/main/java/com/waad/tba/modules/member");

    /**
     * Entities that are legitimately named in a controller signature for a
     * reason other than being the response body. Empty, and meant to stay
     * that way: an entry here is a documented exception, not a shortcut.
     */
    private static final Set<String> ALLOWED = Set.of();

    private static String codeOnly(String source) {
        // Comments name entities constantly -- including the ones above this
        // very rule -- and a guard that reads its own documentation as a
        // violation is one nobody keeps.
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private List<String> entityNames() throws IOException {
        try (Stream<Path> files = Files.list(MODULE.resolve("entity"))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .filter(name -> !ALLOWED.contains(name))
                    .collect(Collectors.toList());
        }
    }

    @Test
    @DisplayName("no member endpoint answers with a persistence entity")
    void noEndpointReturnsAnEntity() throws IOException {
        List<String> entities = entityNames();
        assertThat(entities)
                .as("the entity package should not be empty -- an empty list would make this pass on nothing")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();

        try (Stream<Path> controllers = Files.list(MODULE.resolve("controller"))) {
            for (Path controller : controllers.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeOnly(Files.readString(controller, StandardCharsets.UTF_8));
                for (String line : code.lines().toList()) {
                    if (!line.contains("ResponseEntity<")) {
                        continue;
                    }
                    for (String entity : entities) {
                        // Word boundaries on both sides: MemberImportLogSummaryDto
                        // contains MemberImportLog, and it is the fix, not the
                        // violation.
                        if (line.matches(".*\\b" + entity + "\\b(?![A-Za-z0-9_]).*")) {
                            violations.add(controller.getFileName() + ": " + line.trim());
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("return a DTO. An entity in a response ships every column the table has, "
                        + "now and in future, and makes the screen's contract the schema")
                .isEmpty();
    }

    @Test
    @DisplayName("the response DTO for a table row carries only what a screen reads")
    void theImportSummaryCarriesNoInternalIdentifiers() throws IOException {
        String dto = Files.readString(MODULE.resolve("dto/MemberImportLogSummaryDto.java"), StandardCharsets.UTF_8);
        String code = codeOnly(dto);

        // The three the entity carried and no screen ever wanted. fileHash and
        // importScopeHash are idempotency machinery; importedByUserId is an
        // internal key, and the username beside it is what a person reads.
        assertThat(code).doesNotContain("fileHash");
        assertThat(code).doesNotContain("importScopeHash");
        assertThat(code).doesNotContain("importedByUserId");
    }
}
