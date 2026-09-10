package com.studytime.security;

public record MemberPrincipal(
        String memberId,
        String familyId,
        String displayName,
        String relationName,
        String role,
        String studentId) {
}
