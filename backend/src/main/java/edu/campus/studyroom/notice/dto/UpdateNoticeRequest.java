package edu.campus.studyroom.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Draft update with the optimistic version lock.
 */
public record UpdateNoticeRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字")
        String title,

        @NotBlank(message = "内容不能为空")
        @Size(max = 5000, message = "内容不能超过 5000 字")
        String content,

        Boolean pinned,

        @NotNull(message = "expectedVersion 不能为空")
        Long expectedVersion
) {
}
