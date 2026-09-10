package com.studytime.notification;

import com.studytime.api.ApiModels.NotificationFeedView;
import com.studytime.domain.StudyTimeRepository.StudentRow;
import com.studytime.security.AccessControlService;
import com.studytime.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 50;

    private final NotificationRepository repository;
    private final AccessControlService accessControl;

    public NotificationService(NotificationRepository repository, AccessControlService accessControl) {
        this.repository = repository;
        this.accessControl = accessControl;
    }

    public NotificationFeedView feed(boolean unreadOnly, Integer requestedLimit) {
        MemberPrincipal member = accessControl.currentMember();
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, requestedLimit));
        return new NotificationFeedView(
                repository.unreadCount(member.memberId()),
                repository.findForMember(member.memberId(), unreadOnly, limit));
    }

    @Transactional
    public NotificationFeedView markRead(String notificationId) {
        MemberPrincipal member = accessControl.currentMember();
        repository.markRead(notificationId, member.memberId());
        return feed(false, DEFAULT_LIMIT);
    }

    @Transactional
    public NotificationFeedView markAllRead() {
        MemberPrincipal member = accessControl.currentMember();
        repository.markAllRead(member.memberId());
        return feed(false, DEFAULT_LIMIT);
    }

    public void notifyParents(
            StudentRow student,
            String eventType,
            String title,
            String message,
            String actionPath,
            String dedupeKey) {
        insertForRecipients(
                repository.parentMemberIds(student.familyId()), student, eventType, title, message, actionPath, dedupeKey);
    }

    public void notifyStudent(
            StudentRow student,
            String eventType,
            String title,
            String message,
            String actionPath,
            String dedupeKey) {
        insertForRecipients(
                repository.studentMemberIds(student.id()), student, eventType, title, message, actionPath, dedupeKey);
    }

    private void insertForRecipients(
            List<String> recipients,
            StudentRow student,
            String eventType,
            String title,
            String message,
            String actionPath,
            String dedupeKey) {
        for (String recipient : recipients) {
            repository.insert(
                    UUID.randomUUID().toString(), student.familyId(), student.id(), recipient,
                    eventType, title, message, actionPath, dedupeKey);
        }
    }
}
