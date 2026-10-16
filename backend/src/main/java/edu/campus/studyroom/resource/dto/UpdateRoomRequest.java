package edu.campus.studyroom.resource.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.campus.studyroom.resource.domain.OrderPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * 编辑房间（全量提交）。编号 code 可修改（大写字母/数字/连字符，需查重）；
 * 缩短开放时间时必须提供 orderPolicy（KEEP_ACTIVE/FORCE_END）。
 */
public record UpdateRoomRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{1,32}$", message = "编号须为大写字母/数字/连字符，最长 32 位") String code,
        @NotNull @Size(max = 100) String name,
        @NotNull @Size(max = 64) String campus,
        @NotNull @Size(max = 64) String building,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        @NotNull @Min(1) @Max(100) Integer rows,
        @NotNull @Min(1) @Max(100) Integer cols,
        @NotNull Long expectedVersion,
        OrderPolicy orderPolicy
) {
}
