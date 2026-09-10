package com.studytime.realtime;

import com.studytime.domain.StudyTimeRepository;
import com.studytime.domain.StudyTimeRepository.StudentRow;
import com.studytime.security.MemberPrincipal;
import com.studytime.security.SessionAuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class AuthenticatedStudentHandshakeInterceptor implements HandshakeInterceptor {
    private final SessionAuthenticationService authenticationService;
    private final StudyTimeRepository repository;

    public AuthenticatedStudentHandshakeInterceptor(
            SessionAuthenticationService authenticationService,
            StudyTimeRepository repository) {
        this.authenticationService = authenticationService;
        this.repository = repository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String token = query.getFirst("accessToken");
        String studentId = query.getFirst("studentId");
        MemberPrincipal member = authenticationService.authenticate(token).orElse(null);
        if (member == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (studentId == null || studentId.isBlank()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        StudentRow student;
        try {
            student = repository.requireStudent(studentId);
        } catch (IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        boolean sameFamily = member.familyId().equals(student.familyId());
        boolean allowedStudent = !"STUDENT".equals(member.role()) || student.id().equals(member.studentId());
        if (!sameFamily || !allowedStudent) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put("studentId", student.id());
        attributes.put("memberId", member.memberId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No per-handshake resources to release.
    }
}
