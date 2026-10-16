package edu.campus.studyroom.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员修改学生身份信息（学号/姓名/学院）。
 * 学号 trim 后统一大写并与既有账号查重；学号发生变更时该学生全部会话立即作废。
 */
public record UpdateStudentRequest(
        @NotBlank(message = "学号不能为空")
        @Size(max = 32, message = "学号不能超过 32 字符")
        String account,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过 64 字符")
        String name,

        @Size(max = 100, message = "学院不能超过 100 字符")
        String college
) {
}
