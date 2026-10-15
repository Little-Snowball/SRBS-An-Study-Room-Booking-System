package edu.campus.studyroom.resource.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.campus.studyroom.resource.domain.OrderPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * 编辑房间（全量提交）。缩短营业时间时必须提供 orderPolicy（KEEP_ACTIVE/FORCE_END）；
 * code 不可随普通编辑变更。
 */
public record UpdateRoomRequest(
        @NotNull @Size(max = 100) String name,
        @NotNull @Size(max = 64) String campus,
        @NotNull @Size(max = 64) String building,
        @NotNull @Min(1) @Max(100) Integer floorNo,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        @NotNull @Min(1) @Max(100) Integer rows,
        @NotNull @Min(1) @Max(100) Integer cols,
        @NotNull Long expectedVersion,
        OrderPolicy orderPolicy
) {
}
