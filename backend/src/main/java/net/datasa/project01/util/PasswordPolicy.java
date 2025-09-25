// backend/src/main/java/net/datasa/project01/util/PasswordPolicy.java
package net.datasa.project01.util;

import java.util.regex.Pattern;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    // 8~64자, 영문 대/소문자/숫자/특수문자 각각 1개 이상
    public static final Pattern STRONG =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[~!@#$%^&*()_+\\-={}\\[\\]|:;\"'<>,.?/]).{8,64}$");

    public static boolean isStrong(String raw) {
        return raw != null && STRONG.matcher(raw).matches();
    }

    public static void assertStrong(String raw) {
        if (!isStrong(raw)) {
            throw new IllegalArgumentException("비밀번호는 8~64자이며 영문 대/소문자·숫자·특수문자를 모두 포함해야 합니다.");
        }
    }
}
