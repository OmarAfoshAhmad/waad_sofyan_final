package com.waad.tba.common.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Uploads were admitted on the strength of the Content-Type the client sent.
 * That header is the uploader's word for what the bytes are, so a page of
 * script uploaded as image/png passed the allowlist untouched, kept its .html
 * extension through sanitisation, and landed in the upload directory.
 *
 * Separately, ALLOWED_MEDICAL_TYPES was allow-listed but matched neither the
 * image nor the document size branch, so a DICOM upload had no size ceiling
 * at all.
 */
class UploadContentValidationTest {

    private LocalFileStorageService storage;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("upload-validation");
        storage = new LocalFileStorageService();
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(storage, "maxDocumentSize", 10L * 1024 * 1024);
        ReflectionTestUtils.setField(storage, "maxImageSize", 50L * 1024 * 1024);
        ReflectionTestUtils.setField(storage, "maxMedicalSize", 200L * 1024 * 1024);
        storage.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static final byte[] PNG_HEADER = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    @Test
    void scriptDisguisedAsAnImageIsRejectedOnItsBytes() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "payload.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes());

        assertThatThrownBy(() -> storage.upload(disguised, "attachments"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("does not match declared type");
    }

    @Test
    void aDangerousExtensionIsRefusedEvenWithAConvincingContentType() {
        byte[] content = new byte[PNG_HEADER.length + 4];
        System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);

        MockMultipartFile doubleExtension = new MockMultipartFile(
                "file", "invoice.png.html", "image/png", content);

        assertThatThrownBy(() -> storage.upload(doubleExtension, "attachments"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("extension not allowed");
    }

    @Test
    void anSvgIsRefusedBecauseItCarriesScript() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "logo.svg", "image/png", PNG_HEADER);

        assertThatThrownBy(() -> storage.upload(svg, "attachments"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("extension not allowed");
    }

    @Test
    void aGenuineImageIsStillAccepted() {
        byte[] content = new byte[PNG_HEADER.length + 128];
        System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);

        MockMultipartFile png = new MockMultipartFile("file", "scan.png", "image/png", content);

        assertThatCode(() -> storage.upload(png, "attachments")).doesNotThrowAnyException();
    }

    @Test
    void aGenuinePdfIsStillAccepted() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.7 body".getBytes());

        assertThatCode(() -> storage.upload(pdf, "attachments")).doesNotThrowAnyException();
    }

    @Test
    void medicalImagingNowHasASizeCeiling() {
        ReflectionTestUtils.setField(storage, "maxMedicalSize", 1024L);
        byte[] oversized = new byte[4096];

        MockMultipartFile dicom = new MockMultipartFile(
                "file", "study.dcm", "application/dicom", oversized);

        assertThatThrownBy(() -> storage.upload(dicom, "attachments"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Medical file size exceeds limit");
    }

    @Test
    void pathTraversalInTheFilenameCannotEscapeTheUploadDirectory() {
        byte[] content = new byte[PNG_HEADER.length];
        System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);

        MockMultipartFile traversal = new MockMultipartFile(
                "file", "../../etc/passwd.png", "image/png", content);

        storage.upload(traversal, "attachments");

        assertThat(tempDir.resolve("attachments").toFile().isDirectory())
                .as("the file must land inside the upload root, not above it")
                .isTrue();
    }

    // ---- WebP -------------------------------------------------------------
    // Profile photos are re-encoded to WebP in the browser before upload, so
    // the storage layer has to admit them -- and admit them for the right
    // reason. WebP is a RIFF container, not a prefix signature: "RIFF" at 0,
    // a per-file length, then "WEBP" at 8. A check that only matched "RIFF"
    // would let a WAV file through as a photograph.

    private static byte[] riff(String fourCcAtEight) {
        byte[] bytes = new byte[16];
        System.arraycopy(new byte[] { 0x52, 0x49, 0x46, 0x46 }, 0, bytes, 0, 4); // RIFF
        bytes[4] = 0x08; // per-file length, deliberately not part of the check
        System.arraycopy(fourCcAtEight.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }

    @Test
    void aRealWebpIsAccepted() {
        MockMultipartFile photo = new MockMultipartFile("file", "photo.webp", "image/webp", riff("WEBP"));

        assertThatCode(() -> storage.upload(photo, "photos")).doesNotThrowAnyException();
    }

    @Test
    void anotherRiffContainerIsNotAWebp() {
        // A WAV file starts with the same four bytes. Rejecting it is the
        // whole reason the second half of the header is read.
        MockMultipartFile wav = new MockMultipartFile("file", "sound.webp", "image/webp", riff("WAVE"));

        assertThatThrownBy(() -> storage.upload(wav, "photos"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("does not match declared type");
    }

    @Test
    void aFileTooShortToCarryTheContainerHeaderIsNotAWebp() {
        MockMultipartFile truncated = new MockMultipartFile(
                "file", "tiny.webp", "image/webp", new byte[] { 0x52, 0x49, 0x46, 0x46 });

        assertThatThrownBy(() -> storage.upload(truncated, "photos"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("does not match declared type");
    }
}
