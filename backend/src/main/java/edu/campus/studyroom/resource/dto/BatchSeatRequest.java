package edu.campus.studyroom.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 批量生成座位。编号为 prefix-R两位行号-C两位列号（超过 99 保持完整数字）；
 * 矩形内任何位置或编号已存在则整批失败。
 */
public record BatchSeatRequest(
        @Min(1) int startRow,
        @Min(1) int endRow,
        @Min(1) int startCol,
        @Min(1) int endCol,
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{1,16}$") String codePrefix,
        Boolean hasPower,
        Boolean nearWindow,
        Boolean quietZone
) {
}
