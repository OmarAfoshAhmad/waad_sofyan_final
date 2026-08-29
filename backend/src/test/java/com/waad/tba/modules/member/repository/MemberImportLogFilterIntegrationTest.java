package com.waad.tba.modules.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.member.entity.MemberImportBatchRow;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.entity.MemberImportLog.ImportStatus;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManager;

/**
 * The history screen's filters exist twice: once in JPQL for the global read,
 * once inside the native employer-scoped read, because the scope predicate
 * needs a JSONB subquery JPQL cannot express.
 *
 * Two spellings of one filter set drift, and they drift in the direction that
 * matters most to the person who can least detect it -- the employer-scoped
 * user, who never sees the other path to compare against. So every case here
 * runs through both reads, and the scoped run is given every employer in the
 * data so the only thing that can make them disagree is the filters.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberImportLogFilterIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberImportLogRepository logs;
    @Autowired private MemberImportBatchRowRepository rows;
    @Autowired private EntityManager entityManager;

    private static final long EMPLOYER_A = 8801L;
    private static final long EMPLOYER_B = 8802L;

    /** The JPQL read is sorted by Spring; the native one orders itself. */
    private final PageRequest sortedPage = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    private final PageRequest unsortedPage = PageRequest.of(0, 50);

    @BeforeEach
    void seed() {
        rows.deleteAll();
        logs.deleteAll();

        log("batch-alpha", "employees-january.xlsx", "salma", ImportStatus.COMPLETED,
                LocalDateTime.of(2026, 1, 10, 9, 0), EMPLOYER_A);
        log("batch-beta", "employees-february.xlsx", "salma", ImportStatus.PARTIAL,
                LocalDateTime.of(2026, 2, 14, 9, 0), EMPLOYER_A);
        log("batch-gamma", "contractors.xlsx", "nabil", ImportStatus.FAILED,
                LocalDateTime.of(2026, 3, 20, 9, 0), EMPLOYER_B);
        log("batch-delta", "employees-march.xlsx", "nabil", ImportStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 25, 9, 0), EMPLOYER_B);
        entityManager.flush();
        entityManager.clear();
    }

    private void log(String batchId, String fileName, String username, ImportStatus status,
            LocalDateTime createdAt, long employerId) {
        MemberImportLog row = MemberImportLog.builder().importBatchId(batchId).build();
        row.setFileName(fileName);
        row.setImportedByUsername(username);
        row.setStatus(status);
        row.setStartedAt(createdAt);
        MemberImportLog saved = logs.saveAndFlush(row);

        // createdAt is @CreatedDate: auditing stamps it with now() on save and
        // the column is updatable = false, so a date-range test that sets it
        // through the entity is testing four rows that all say "today".
        entityManager.createNativeQuery("update member_import_logs set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, saved.getId())
                .executeUpdate();

        // The scoped read only sees a batch through its rows, so a log with no
        // rows is invisible to it whatever the filters say.
        rows.save(MemberImportBatchRow.builder()
                .importLogId(saved.getId())
                .memberId(saved.getId())
                .action(MemberImportBatchRow.Action.CREATED)
                .importedSnapshot("{\"employerId\": " + employerId + "}")
                .build());
    }

    /** The caller builds the like-pattern; the query only matches it. */
    private static String pattern(String search) {
        return search == null ? null : "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
    }

    /** Both reads, same filters, same rows. */
    private void bothAgree(ImportStatus status, String search, LocalDateTime from, LocalDateTime to,
            String... expected) {
        List<String> global = logs.findFiltered(status, pattern(search), from, to, sortedPage)
                .map(MemberImportLog::getImportBatchId).getContent();
        List<String> scoped = logs.findVisibleToEmployers(Set.of(EMPLOYER_A, EMPLOYER_B),
                status == null ? null : status.name(), pattern(search), from, to, unsortedPage)
                .map(MemberImportLog::getImportBatchId).getContent();

        assertThat(global).as("global read").containsExactlyInAnyOrder(expected);
        assertThat(scoped).as("employer-scoped read, given every employer in the data")
                .containsExactlyInAnyOrderElementsOf(global);
    }

    @Test
    @Transactional
    @DisplayName("no filters returns everything, on both reads")
    void noFilters() {
        bothAgree(null, null, null, null, "batch-alpha", "batch-beta", "batch-gamma", "batch-delta");
    }

    @Test
    @Transactional
    @DisplayName("status narrows to that status alone")
    void byStatus() {
        bothAgree(ImportStatus.COMPLETED, null, null, null, "batch-alpha", "batch-delta");
        bothAgree(ImportStatus.FAILED, null, null, null, "batch-gamma");
    }

    @Test
    @Transactional
    @DisplayName("search covers the file name, the batch id and who ran it")
    void searchCoversTheThreeThingsSomeoneArrivesWith() {
        bothAgree(null, "february", null, null, "batch-beta");
        bothAgree(null, "batch-gamma", null, null, "batch-gamma");
        bothAgree(null, "nabil", null, null, "batch-gamma", "batch-delta");
    }

    @Test
    @Transactional
    @DisplayName("search is case-insensitive and matches inside the name")
    void searchIsCaseInsensitiveAndPartial() {
        bothAgree(null, "EMPLOYEES", null, null, "batch-alpha", "batch-beta", "batch-delta");
    }

    @Test
    @Transactional
    @DisplayName("the date range is half-open: the from-bound is in, the to-bound is out")
    void theDateRangeBoundaries() {
        bothAgree(null, null, LocalDateTime.of(2026, 2, 14, 9, 0), null,
                "batch-beta", "batch-gamma", "batch-delta");

        bothAgree(null, null, null, LocalDateTime.of(2026, 3, 20, 9, 0),
                "batch-alpha", "batch-beta");
    }

    @Test
    @Transactional
    @DisplayName("filters combine rather than replace each other")
    void filtersCombine() {
        bothAgree(ImportStatus.COMPLETED, "employees", LocalDateTime.of(2026, 3, 1, 0, 0), null,
                "batch-delta");
    }

    @Test
    @Transactional
    @DisplayName("the scope still wins: no filter widens what an employer can see")
    void filtersNeverWidenTheScope() {
        List<String> onlyA = logs.findVisibleToEmployers(Set.of(EMPLOYER_A), null, null, null, null, unsortedPage)
                .map(MemberImportLog::getImportBatchId).getContent();

        assertThat(onlyA).containsExactlyInAnyOrder("batch-alpha", "batch-beta");

        // Searching for the other employer's file by name must find nothing,
        // rather than confirming it exists.
        List<String> searchingForTheOtherEmployersFile = logs
                .findVisibleToEmployers(Set.of(EMPLOYER_A), null, pattern("contractors"), null, null, unsortedPage)
                .map(MemberImportLog::getImportBatchId).getContent();

        assertThat(searchingForTheOtherEmployersFile).isEmpty();
    }
}
