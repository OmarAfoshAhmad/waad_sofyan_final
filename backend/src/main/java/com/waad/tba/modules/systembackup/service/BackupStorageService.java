package com.waad.tba.modules.systembackup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Filesystem and PostgreSQL tooling behind the backup module.
 *
 * Every external process runs with an explicit timeout: a stalled pg_dump would
 * otherwise hang forever while holding the backup lock, blocking both manual and
 * scheduled backups indefinitely.
 *
 * This service is deliberately read-only with respect to the live database. The
 * destructive restore path (pg_restore into the running database) is NOT part of
 * this module — it belongs to the guarded danger-zone feature.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupStorageService {

    private static final String DB_DUMP_ENTRY = "database/database.dump";

    private final Environment environment;

    @Value("${file.storage.local.base-dir:./storage/uploads}")
    private String uploadsBaseDir;

    /** Max wall-clock time for pg_dump before it is killed. */
    @Value("${waad.backup.pg-dump-timeout-seconds:1800}")
    private long pgDumpTimeoutSeconds;

    /** Max wall-clock time for the read-only pg_restore --list check. */
    @Value("${waad.backup.pg-restore-list-timeout-seconds:120}")
    private long pgRestoreListTimeoutSeconds;

    public Path createWorkingDirectory(Path backupRoot, Long backupId) throws IOException {
        Path dir = backupRoot.resolve("work").resolve("backup-" + backupId).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    public Path uploadsPath() {
        return Path.of(uploadsBaseDir).toAbsolutePath().normalize();
    }

    public Path dumpDatabase(Path workingDir, String fileName, List<String> warnings)
            throws IOException, InterruptedException {
        Path dumpFile = workingDir.resolve(fileName).toAbsolutePath().normalize();
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "postgres");
        String password = environment.getProperty("spring.datasource.password", "");

        PgTarget target = parseJdbcUrl(jdbcUrl);
        List<String> command = List.of(
                "pg_dump",
                "-h", target.host(),
                "-p", String.valueOf(target.port()),
                "-U", username,
                "-F", "c",
                "-f", dumpFile.toString(),
                target.database()
        );

        ProcessResult result = runProcess(
                command,
                Map.of("PGPASSWORD", password == null ? "" : password),
                pgDumpTimeoutSeconds,
                password);

        if (!result.ok()) {
            throw new IllegalStateException(
                    "pg_dump failed. Ensure pg_dump is installed and reachable by the backend runtime. "
                            + result.output());
        }
        warnings.add("تم إنشاء نسخة قاعدة البيانات عبر pg_dump من بيئة تشغيل الـ backend بدون استخدام docker.sock.");
        return dumpFile;
    }

    public void addPathToZip(ZipOutputStream zip, Path sourceRoot, String zipPrefix, List<String> warnings)
            throws IOException {
        if (!Files.exists(sourceRoot)) {
            warnings.add("مسار الملفات غير موجود: " + sourceRoot);
            return;
        }
        if (!Files.isDirectory(sourceRoot)) {
            warnings.add("مسار الملفات ليس مجلدًا: " + sourceRoot);
            return;
        }
        try (var stream = Files.walk(sourceRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                Path relative = sourceRoot.relativize(path);
                String entryName = zipPrefix + "/" + relative.toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    public Path writeZip(Path target, ZipWriter writer) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
            writer.write(zip);
        }
        return target;
    }

    public boolean isWritableDirectory(Path path) {
        try {
            Files.createDirectories(path);
            Path probe = Files.createTempFile(path, ".waad-backup-probe", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long usableSpace(Path path) {
        try {
            Files.createDirectories(path);
            return Files.getFileStore(path).getUsableSpace();
        } catch (Exception e) {
            return null;
        }
    }

    public String environmentName() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "default";
        }
        return String.join(",", profiles).toLowerCase(Locale.ROOT);
    }

    public List<String> includedComponents(boolean database, boolean files) {
        List<String> components = new ArrayList<>();
        if (database) {
            components.add("database");
        }
        if (files) {
            components.add("uploads");
        }
        components.add("manifest");
        return components;
    }

    // ===================== Archive inspection (read-only) =====================

    /** True if the archive contains a database dump entry. */
    public boolean archiveContainsDatabaseDump(Path archive) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.getEntry(DB_DUMP_ENTRY) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Read the manifest.json entry from the archive; returns null if unreadable. */
    public String readManifestFromArchive(Path archive) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract the database dump entry to a temp file for inspection; caller deletes the parent dir. */
    public Path extractDatabaseDump(Path archive, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        Path out = workDir.resolve("database.dump").toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(DB_DUMP_ENTRY);
            if (entry == null) {
                throw new IOException("Archive has no database dump entry");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return out;
    }

    /**
     * Safe restore-readability check: {@code pg_restore --list} parses the dump's table of
     * contents WITHOUT touching any database, so it is safe to run in production.
     */
    public ProcessResult pgRestoreList(Path dumpFile) {
        try {
            return runProcess(
                    List.of("pg_restore", "--list", dumpFile.toString()),
                    Map.of(),
                    pgRestoreListTimeoutSeconds,
                    null);
        } catch (Exception e) {
            return new ProcessResult(-1, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    // ===================== Process execution =====================

    /**
     * Runs an external command with a hard timeout, capturing merged stdout/stderr.
     *
     * The output is drained on a separate thread: reading the stream inline would block
     * until the child exits, which would make the timeout unreachable for a stalled process.
     *
     * @param secretToRedact if non-null, every occurrence is masked in the captured output
     */
    private ProcessResult runProcess(List<String> command,
                                     Map<String, String> extraEnv,
                                     long timeoutSeconds,
                                     String secretToRedact) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(extraEnv);
        builder.redirectErrorStream(true);

        Process process = builder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try (InputStream in = process.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            outputFuture.cancel(true);
            log.warn("[BKP] Command {} timed out after {}s and was terminated", command.get(0), timeoutSeconds);
            throw new IllegalStateException(
                    "انتهت المهلة المحددة للأمر " + command.get(0) + " بعد " + timeoutSeconds + " ثانية وتم إيقافه.");
        }

        String output;
        try {
            output = outputFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            output = "";
        }
        return new ProcessResult(process.exitValue(), redact(output, secretToRedact));
    }

    private static String redact(String output, String secret) {
        if (output == null) {
            return "";
        }
        if (secret == null || secret.isEmpty()) {
            return output;
        }
        return output.replace(secret, "[REDACTED]");
    }

    private PgTarget parseJdbcUrl(String jdbcUrl) {
        try {
            String raw = jdbcUrl.replaceFirst("^jdbc:", "");
            URI uri = URI.create(raw);
            String db = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            if (db.isBlank()) {
                throw new IllegalArgumentException("Database name missing");
            }
            return new PgTarget(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 5432, db);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse PostgreSQL JDBC URL for backup", e);
        }
    }

    public interface ZipWriter {
        void write(ZipOutputStream zip) throws IOException;
    }

    public record ProcessResult(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    private record PgTarget(String host, int port, String database) {
    }
}
