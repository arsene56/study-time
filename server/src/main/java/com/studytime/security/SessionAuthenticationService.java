package com.studytime.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SessionAuthenticationService {
    private static final int MAX_TOKEN_LENGTH = 200;

    private final JdbcClient jdbc;

    public SessionAuthenticationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MemberPrincipal> authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT m.id, m.family_id, m.display_name, m.relation_name, m.role, m.student_id
                        FROM member_sessions s
                        JOIN members m ON m.id = s.member_id
                        WHERE s.token_hash = :tokenHash
                          AND s.revoked_at IS NULL
                          AND (s.expires_at IS NULL OR s.expires_at > CURRENT_TIMESTAMP)
                        """)
                .param("tokenHash", sha256(token))
                .query((rs, rowNum) -> new MemberPrincipal(
                        rs.getString("id"),
                        rs.getString("family_id"),
                        rs.getString("display_name"),
                        rs.getString("relation_name"),
                        rs.getString("role"),
                        rs.getString("student_id")))
                .optional();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }
}
