package edu.campus.studyroom.appeal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Appeal submission. The reason is plain text (10-1000 chars) and at most three
 * evidence files may be attached.
 */
public record SubmitAppealRequest(
        @NotNull(message = "violationId 不能为空")
        Long violationId,

        @NotBlank(message = "申诉理由不能为空")
        @Size(min = 10, max = 1000, message = "申诉理由须为 10—1000 字")
        String reason,

        @Size(max = 3, message = "每个申诉最多上传 3 张凭证")
        List<@NotNull(message = "fileIds 不能包含空元素") Long> fileIds
) {
}
