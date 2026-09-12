package com.waad.tba.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.file.FileStorageException;
import com.waad.tba.common.file.LocalFileStorageService;
import com.waad.tba.modules.member.service.UnifiedMemberService;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Ten controllers used to catch exceptions locally and write
 * "<prefix>: " + e.getMessage() into the response. For a business rule that
 * was merely a lost tracking id and a wrong status; for a storage or parser
 * failure it was a file-system path or a POI internal shown to the user.
 *
 * The contract now: every failure crosses GlobalExceptionHandler, arrives
 * with a stable code, a tracking id and an Arabic message, and carries none
 * of the exception's own text.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControllersDoNotLeakExceptionTextTest extends PostgresIntegrationTestBase {

    private static final String LEAKY_PATH = "/srv/waad/uploads/members/photos/secret-3.png";

    @Autowired MockMvc mockMvc;
    @MockitoBean PermissionGuard permissionGuard;
    // The concrete class: one service injects it by its implementation type,
    // so a mock of the interface alone would leave that bean unsatisfiable.
    @MockitoBean LocalFileStorageService fileStorageService;
    // Access check and persistence are mocked away: what this test proves is
    // the shape of the failure response, which the handler alone decides.
    @MockitoBean UnifiedMemberService unifiedMemberService;

    // ── business rule: Arabic reason, stable code, 422 — no longer a bare 400 ──

    @Test
    @WithMockUser(username = "reviewer")
    void malformedEligibilityQueryAnswersWithACodeAndAnArabicReason() throws Exception {
        when(permissionGuard.has(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/members/eligibility/evaluations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"!!not-a-card!!\",\"serviceDate\":\"2026-09-12\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.trackingId").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("صيغة الإدخال")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    // ── storage failure: the path in the exception never reaches the body ──

    @Test
    @WithMockUser(username = "clerk")
    void storageFailureOnPhotoUploadHidesTheFileSystemPath() throws Exception {
        when(permissionGuard.has(anyString())).thenReturn(true);
        when(fileStorageService.upload(any(), anyString()))
                .thenThrow(new FileStorageException("Failed to store file: " + LEAKY_PATH));

        MockMultipartFile photo = new MockMultipartFile("file", "me.png", "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 });

        mockMvc.perform(multipart("/api/v1/unified-members/1/photo").file(photo).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILE_OPERATION_FAILED"))
                .andExpect(jsonPath("$.trackingId").isNotEmpty())
                .andExpect(jsonPath("$.messageAr").value("تعذر معالجة الملف. حاول مرة أخرى."))
                .andExpect(content().string(not(containsString(LEAKY_PATH))))
                .andExpect(content().string(not(containsString("/srv"))));
    }

    // ── and the shape of the fix, so a new controller cannot bring it back ──

    @Test
    void noControllerWritesAnExceptionMessageIntoAResponse() throws Exception {
        Path root = Path.of("src/main/java/com/waad/tba");
        // ApiResponse.error(... e.getMessage() ...) or .body(... e.getMessage() ...),
        // possibly split across two lines.
        Pattern leak = Pattern.compile("(ApiResponse\\.error|\\.body)\\s*\\([^;]*?\\b\\w+\\.getMessage\\(\\)", Pattern.DOTALL);
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                var m = leak.matcher(source);
                while (m.find()) {
                    String hit = m.group();
                    // A domain result object's message is data, not an exception.
                    if (hit.contains("result.getMessage()")) continue;
                    offenders.add(root.relativize(file) + ": " + hit.replaceAll("\\s+", " "));
                }
            }
        }
        assertThat(offenders).as("controllers concatenating exception text into responses").isEmpty();
    }
}
