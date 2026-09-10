package com.studytime.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlanUpdateWebSocketHandler extends TextWebSocketHandler {
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByStudent = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object authorizedStudentId = session.getAttributes().get("studentId");
        String studentId = authorizedStudentId == null ? null : authorizedStudentId.toString();
        if (studentId == null || studentId.isBlank()) {
            session.close(CloseStatus.BAD_DATA.withReason("studentId is required"));
            return;
        }
        sessionsByStudent.computeIfAbsent(studentId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object studentId = session.getAttributes().get("studentId");
        if (studentId != null) {
            Set<WebSocketSession> sessions = sessionsByStudent.get(studentId.toString());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByStudent.remove(studentId.toString());
                }
            }
        }
    }

    public void publish(String studentId, String type) {
        String payload = "{\"type\":\"" + type + "\",\"studentId\":\"" + studentId
                + "\",\"occurredAt\":\"" + Instant.now() + "\"}";
        Set<WebSocketSession> sessions = sessionsByStudent.getOrDefault(studentId, Set.of());
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException ignored) {
                // A later reconnect reloads the current plan from the database.
            }
        }
    }
}
