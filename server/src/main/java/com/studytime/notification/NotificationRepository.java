package com.studytime.notification;

import com.studytime.api.ApiModels.NotificationView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class NotificationRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> parentMemberIds(String familyId) {
        return jdbc.sql("SELECT id FROM members WHERE family_id = :familyId AND role = 'PARENT' ORDER BY id")
                .param("familyId", familyId)
                .query(String.class)
                .list();
    }

    public List<String> studentMemberIds(String studentId) {
        return jdbc.sql("SELECT id FROM members WHERE student_id = :studentId AND role = 'STUDENT'")
                .param("studentId", studentId)
                .query(String.class)
                .list();
    }

    public void insert(
            String id,
            String familyId,
            String studentId,
            String recipientMemberId,
            String eventType,
            String title,
            String message,
            String actionPath,
            String dedupeKey) {
        jdbc.sql("""
                        INSERT IGNORE INTO notifications
                            (id, family_id, student_id, recipient_member_id, event_type,
                             title, message, action_path, dedupe_key)
                        VALUES
                            (:id, :familyId, :studentId, :recipientMemberId, :eventType,
                             :title, :message, :actionPath, :dedupeKey)
                        """)
                .param("id", id)
                .param("familyId", familyId)
                .param("studentId", studentId)
                .param("recipientMemberId", recipientMemberId)
                .param("eventType", eventType)
                .param("title", title)
                .param("message", message)
                .param("actionPath", actionPath)
                .param("dedupeKey", dedupeKey)
                .update();
    }

    public int unreadCount(String memberId) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE recipient_member_id = :memberId AND read_at IS NULL")
                .param("memberId", memberId)
                .query(Integer.class)
                .single();
    }

    public List<NotificationView> findForMember(String memberId, boolean unreadOnly, int limit) {
        return jdbc.sql("""
                        SELECT n.id, n.student_id, s.name AS student_name, n.event_type,
                               n.title, n.message, n.action_path, n.read_at, n.created_at
                        FROM notifications n
                        JOIN students s ON s.id = n.student_id
                        WHERE n.recipient_member_id = :memberId
                          AND (:unreadOnly = FALSE OR n.read_at IS NULL)
                        ORDER BY n.created_at DESC
                        LIMIT :limit
                        """)
                .param("memberId", memberId)
                .param("unreadOnly", unreadOnly)
                .param("limit", limit)
                .query((rs, rowNum) -> new NotificationView(
                        rs.getString("id"),
                        rs.getString("student_id"),
                        rs.getString("student_name"),
                        rs.getString("event_type"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getString("action_path"),
                        rs.getTimestamp("read_at") != null,
                        format(rs.getTimestamp("created_at").toLocalDateTime())))
                .list();
    }

    public void markRead(String notificationId, String memberId) {
        jdbc.sql("""
                        UPDATE notifications
                        SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP(6))
                        WHERE id = :notificationId AND recipient_member_id = :memberId
                        """)
                .param("notificationId", notificationId)
                .param("memberId", memberId)
                .update();
    }

    public void markAllRead(String memberId) {
        jdbc.sql("""
                        UPDATE notifications
                        SET read_at = CURRENT_TIMESTAMP(6)
                        WHERE recipient_member_id = :memberId AND read_at IS NULL
                        """)
                .param("memberId", memberId)
                .update();
    }

    private String format(LocalDateTime value) {
        return value.format(DATE_TIME);
    }
}
