package edu.campus.studyroom.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Draft creation. Title and content are stored as plain text; HTML is never
 * rendered by the backend and the frontend escapes on display.
 */
public record CreateNoticeRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字")
        String title,

        @NotBlank(message = "内容不能为空")
        @Size(max = 5000, message = "内容不能超过 5000 字")
        String content,

        Boolean pinned
) {
}
