package edu.campus.studyroom.security;

import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password policy: 6-12 chars, not blank; no other content restrictions.
 * BCrypt cost=12.
 */
@Component
public class PasswordPolicy {

    public static final int COST = 12;
    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 12;

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
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && hash != null && encoder.matches(rawPassword, hash);
    }
}
