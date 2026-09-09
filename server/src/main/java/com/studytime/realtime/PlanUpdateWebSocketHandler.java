package com.studytime.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlanUpdateWebSocketHandler extends TextWebSocketHandler {
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByChild = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String childId = childId(session);
        if (childId == null || childId.isBlank()) {
            session.close(CloseStatus.BAD_DATA.withReason("childId is required"));
            return;
        }
        session.getAttributes().put("childId", childId);
        sessionsByChild.computeIfAbsent(childId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object childId = session.getAttributes().get("childId");
        if (childId != null) {
            Set<WebSocketSession> sessions = sessionsByChild.get(childId.toString());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByChild.remove(childId.toString());
                }
            }
        }
    }

    public void publish(String childId, String type) {
        String payload = "{\"type\":\"" + type + "\",\"childId\":\"" + childId
                + "\",\"occurredAt\":\"" + Instant.now() + "\"}";
        Set<WebSocketSession> sessions = sessionsByChild.getOrDefault(childId, Set.of());
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

    private String childId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("childId");
    }
}
