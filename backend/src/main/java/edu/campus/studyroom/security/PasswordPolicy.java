package edu.campus.studyroom.security;

import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password policy: 12-64 chars, must not contain the account or the user's name,
 * no leading/trailing trimming of the raw secret. BCrypt cost=12.
 */
@Component
public class PasswordPolicy {

    public static final int COST = 12;
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(COST);

    public PasswordEncoder encoder() {
        return encoder;
    }

    public void validate(String rawPassword, String account, String name) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "密码长度须为 " + MIN_LENGTH + "—" + MAX_LENGTH + " 位");
        }
        if (rawPassword.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码不能为空白字符");
        }
        String lower = rawPassword.toLowerCase();
        if (account != null && !account.isBlank() && lower.contains(account.trim().toLowerCase())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码不能包含学号/工号");
        }
        if (name != null && !name.isBlank() && name.trim().length() >= 2
                && lower.contains(name.trim().toLowerCase())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码不能包含姓名");
        }
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && hash != null && encoder.matches(rawPassword, hash);
    }
}
