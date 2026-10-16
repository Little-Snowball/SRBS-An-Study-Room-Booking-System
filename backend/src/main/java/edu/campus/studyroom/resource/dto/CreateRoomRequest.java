package edu.campus.studyroom.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/** 新建自习室请求。checkinCode 为六位数字字符串（允许前导零）。 */
public record CreateRoomRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{1,32}$") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 64) String campus,
        @NotBlank @Size(max = 64) String building,
        @NotNull @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @NotNull @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        @Min(1) @Max(100) int rows,
        @Min(1) @Max(100) int cols,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String checkinCode
) {
}
