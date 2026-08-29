package com.waad.tba.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private String status;
    private String message;
    private String messageAr;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status("success")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String messageEn, String messageAr) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(messageEn)
                .messageAr(messageAr)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * A failure that still carries what the operation found out.
     *
     * The message-only form throws the payload away, which is right for a
     * refusal with nothing to say and wrong for one that produced per-row
     * reasons before it stopped. An import that failed on row 1 knows which
     * row and why; discarding that leaves the screen with nothing to show.
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        ApiResponse<T> response = error(message);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .status("error")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
