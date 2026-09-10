package com.studytime.security;

import com.studytime.domain.StudyTimeRepository.StudentRow;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    public MemberPrincipal currentMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("请先完成身份验证");
        }
        return principal;
    }

    public MemberPrincipal requireFamily(String familyId) {
        MemberPrincipal principal = currentMember();
        if (!principal.familyId().equals(familyId)) {
            throw new AccessDeniedException("无权访问其他家庭的数据");
        }
        return principal;
    }

    public MemberPrincipal requireStudentAccess(StudentRow student) {
        MemberPrincipal principal = requireFamily(student.familyId());
        if ("STUDENT".equals(principal.role()) && !student.id().equals(principal.studentId())) {
            throw new AccessDeniedException("无权访问其他学生的数据");
        }
        return principal;
    }

    public MemberPrincipal requireParentAccess(StudentRow student) {
        MemberPrincipal principal = requireStudentAccess(student);
        if (!"PARENT".equals(principal.role())) {
            throw new AccessDeniedException("该操作需要家长身份");
        }
        return principal;
    }

    public MemberPrincipal requireOwnStudent(StudentRow student) {
        MemberPrincipal principal = requireStudentAccess(student);
        if (!"STUDENT".equals(principal.role()) || !student.id().equals(principal.studentId())) {
            throw new AccessDeniedException("该操作需要对应的学生身份");
        }
        return principal;
    }
}
