package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CohortSession;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.model.SessionAttendance;
import uk.co.motormovers.platform.training.model.SessionJoin;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.SessionAttendanceRepository;
import uk.co.motormovers.platform.training.repository.SessionJoinRepository;
import uk.co.motormovers.platform.training.zoom.ZoomClient;
import uk.co.motormovers.platform.training.zoom.ZoomProperties;
import uk.co.motormovers.platform.training.zoom.ZoomSignature;
import uk.co.motormovers.platform.user.model.Role;

/**
 * Live course sessions: their dates, joining them on Zoom inside the platform, and the register. The Motor
 * Movers team hosts; learners booked on the cohort join, from 15 minutes before the start.
 */
@Service
public class LiveSessionService {

    private final CohortSessionRepository sessions;
    private final SessionAttendanceRepository attendance;
    private final SessionJoinRepository joins;
    private final EnrolmentRepository enrolments;
    private final CurrentUserResolver currentUser;
    private final ZoomClient zoom;
    private final ZoomProperties zoomProperties;
    private final Clock clock;

    public LiveSessionService(CohortSessionRepository sessions, SessionAttendanceRepository attendance,
            SessionJoinRepository joins, EnrolmentRepository enrolments, CurrentUserResolver currentUser, ZoomClient zoom,
            ZoomProperties zoomProperties, Clock clock) {
        this.sessions = sessions;
        this.attendance = attendance;
        this.joins = joins;
        this.enrolments = enrolments;
        this.currentUser = currentUser;
        this.zoom = zoom;
        this.zoomProperties = zoomProperties;
        this.clock = clock;
    }

    /**
     * Dates the cohort's sessions weekly from its start date at its start time, one per live session of the
     * course. A session that already has attendance keeps its date, and is never removed.
     */
    @Transactional
    public void schedule(Cohort cohort) {
        ZoneId zone = clock.getZone();
        int count = cohort.getCourse().getLiveSessions();
        int minutes = cohort.getCourse().getSessionMinutes();
        Map<Integer, CohortSession> existing = sessions.findByCohortIdOrderByNumber(cohort.getId()).stream()
                .collect(Collectors.toMap(CohortSession::getNumber, s -> s));
        for (int n = 1; n <= count; n++) {
            Instant startsAt = cohort.getStartsOn().plusWeeks(n - 1L).atTime(cohort.getStartTime()).atZone(zone)
                    .toInstant();
            CohortSession session = existing.remove(n);
            if (session == null) {
                sessions.save(new CohortSession(cohort, n, startsAt, minutes));
            } else if (!attendance.existsByIdSessionId(session.getId())) {
                session.reschedule(startsAt, minutes);
            }
        }
        existing.values().stream().filter(s -> !attendance.existsByIdSessionId(s.getId())).forEach(sessions::delete);
    }

    /** The team sees every session still to come; anyone else sees the sessions of cohorts they booked. */
    @Transactional(readOnly = true)
    public List<TrainingViews.LiveSessionView> upcoming() {
        CurrentUser me = currentUser.current();
        Instant now = clock.instant();
        Instant from = now.minus(CohortSession.CLOSES_AFTER).minusSeconds(24 * 60 * 60);
        List<CohortSession> list;
        if (me.is(Role.ADMIN)) {
            list = sessions.findByStartsAtAfterOrderByStartsAt(from);
        } else {
            Set<Long> cohortIds = enrolments.findByUserIdOrderByEnrolledAtDesc(me.id()).stream()
                    .map(e -> e.getCohort().getId()).collect(Collectors.toSet());
            list = cohortIds.isEmpty() ? List.of()
                    : sessions.findByCohortIdInAndStartsAtAfterOrderByStartsAt(cohortIds, from);
        }
        return list.stream().filter(s -> !s.isOverAt(now)).map(s -> view(s, me, now)).toList();
    }

    @Transactional
    public TrainingViews.JoinView join(Long sessionId) {
        CurrentUser me = currentUser.current();
        Instant now = clock.instant();
        CohortSession session = find(sessionId);
        boolean host = me.is(Role.ADMIN);
        if (!host) {
            Enrolment booked = enrolments.findByCohortIdAndUserId(session.getCohort().getId(), me.id())
                    .orElseThrow(() -> new AccessDeniedException("You are not booked on this course"));
            if (session.isOverAt(now)) {
                throw new IllegalStateException("This session has finished");
            }
            if (!session.isOpenAt(now)) {
                throw new IllegalStateException("This session opens 15 minutes before it starts");
            }
            recordJoin(sessionId, booked.getId(), now);
        }
        TrainingViews.LiveSessionView view = view(session, me, now);
        Cohort cohort = session.getCohort();
        if (!zoomProperties.configured()) {
            return new TrainingViews.JoinView(view, false, false, null, null, null, null, null, null, me.displayName(),
                    me.email(), TrainingViews.LIVE_SESSION_RULES, cohort.isRecordingAgreed());
        }
        String hostUser = hostUser(me);
        // The host's token first, before locking the session: if Zoom refuses it, no meeting is created for nothing.
        String zak = host ? zoom.hostToken(hostUser) : null;
        CohortSession locked = sessions.findForUpdate(sessionId).orElseThrow();
        if (locked.getZoomMeetingId() == null) {
            ZoomClient.Meeting meeting = zoom.createMeeting(hostUser,
                    cohort.getCourse().getTitle() + " · " + cohort.getName() + " · session " + locked.getNumber(),
                    locked.getStartsAt(), locked.getDurationMinutes(), clock.getZone(), cohort.isRecordingAgreed());
            locked.attachMeeting(meeting.id(), meeting.passcode(), meeting.joinUrl());
        }
        String meetingId = locked.getZoomMeetingId();
        if (!zoomProperties.embedded()) {
            // No Meeting SDK app: the session opens in Zoom. The host starts it with their token, signed in as
            // the host without a Zoom password; learners use the meeting's join link.
            String url = host ? "https://zoom.us/s/" + meetingId + "?zak=" + zak
                    : locked.getZoomJoinUrl() != null ? locked.getZoomJoinUrl() : "https://zoom.us/j/" + meetingId;
            return new TrainingViews.JoinView(view, true, false, url, null, null, meetingId, locked.getZoomPasscode(),
                    null, me.displayName(), me.email(), TrainingViews.LIVE_SESSION_RULES, cohort.isRecordingAgreed());
        }
        String signature = ZoomSignature.sign(zoomProperties.sdkKey(), zoomProperties.sdkSecret(), meetingId,
                host ? ZoomSignature.HOST : ZoomSignature.PARTICIPANT, now);
        return new TrainingViews.JoinView(view, true, true, null, zoomProperties.sdkKey(), signature, meetingId,
                locked.getZoomPasscode(), zak, me.displayName(), me.email(),
                TrainingViews.LIVE_SESSION_RULES, cohort.isRecordingAgreed());
    }

    /** Who is booked, and who has been marked present at this session. The team only. */
    @Transactional(readOnly = true)
    public TrainingViews.RegisterView register(Long sessionId) {
        CurrentUser me = requireTeam();
        CohortSession session = find(sessionId);
        Set<Long> present = attendance.findByIdSessionId(sessionId).stream().map(a -> a.getId().enrolmentId())
                .collect(Collectors.toSet());
        Map<Long, SessionJoin> joined = joins.findByIdSessionId(sessionId).stream()
                .collect(Collectors.toMap(j -> j.getId().enrolmentId(), j -> j));
        List<TrainingViews.RegisterRow> rows = enrolments.findByCohortIdOrderByEnrolledAt(session.getCohort().getId())
                .stream()
                .map(e -> {
                    SessionJoin j = joined.get(e.getId());
                    return new TrainingViews.RegisterRow(e.getId(), e.getUser().getDisplayName(),
                            e.getUser().getEmail(), present.contains(e.getId()), e.getSessionsAttended(),
                            e.getCertificateIssuedOn() != null, j == null ? null : j.getFirstJoinedAt(),
                            j == null ? null : j.getLastJoinedAt());
                })
                .toList();
        return new TrainingViews.RegisterView(view(session, me, clock.instant()), rows);
    }

    /**
     * Marks a learner present (or not) at a session, updates their sessions attended, and issues the
     * certificate once every session is attended and the assessment passed. The team only.
     */
    @Transactional
    public TrainingViews.EnrolmentView mark(Long sessionId, Long enrolmentId, boolean present) {
        CurrentUser me = requireTeam();
        CohortSession session = find(sessionId);
        Enrolment enrolment = enrolments.findById(enrolmentId)
                .orElseThrow(() -> new NotFoundException("Enrolment", enrolmentId));
        if (!enrolment.getCohort().getId().equals(session.getCohort().getId())) {
            throw new IllegalArgumentException("This learner is not booked on this cohort");
        }
        if (enrolment.getCertificateIssuedOn() != null) {
            throw new IllegalStateException("The certificate has been issued, so attendance can no longer be changed");
        }
        SessionAttendance.Key key = new SessionAttendance.Key(sessionId, enrolmentId);
        if (present && !attendance.existsById(key)) {
            attendance.save(new SessionAttendance(sessionId, enrolmentId, me.email(), clock.instant()));
        } else if (!present) {
            attendance.deleteById(key);
        }
        attendance.flush();
        enrolment.attendanceChanged((int) attendance.countByIdEnrolmentId(enrolmentId));
        enrolment.issueCertificateIfEligible(LocalDate.now(clock));
        return TrainingViews.EnrolmentView.of(enrolment, attendance.attendedNumbers(enrolmentId));
    }

    /**
     * Ticks everyone who opened this session and is not ticked yet, leaving anyone whose certificate is already
     * issued as it is. The team only.
     */
    @Transactional
    public TrainingViews.RegisterView markJoinedPresent(Long sessionId) {
        requireTeam();
        Set<Long> present = attendance.findByIdSessionId(sessionId).stream().map(a -> a.getId().enrolmentId())
                .collect(Collectors.toSet());
        for (SessionJoin j : joins.findByIdSessionId(sessionId)) {
            Long enrolmentId = j.getId().enrolmentId();
            boolean locked = enrolments.findById(enrolmentId).map(e -> e.getCertificateIssuedOn() != null).orElse(true);
            if (!present.contains(enrolmentId) && !locked) {
                mark(sessionId, enrolmentId, true);
            }
        }
        return register(sessionId);
    }

    private void recordJoin(Long sessionId, Long enrolmentId, Instant now) {
        joins.findById(new SessionAttendance.Key(sessionId, enrolmentId))
                .ifPresentOrElse(j -> j.joinedAgain(now), () -> joins.save(new SessionJoin(sessionId, enrolmentId, now)));
    }

    public List<TrainingViews.SessionSummary> summaries(Long cohortId) {
        return sessions.findByCohortIdOrderByNumber(cohortId).stream()
                .map(s -> new TrainingViews.SessionSummary(s.getId(), s.getNumber(), s.getStartsAt(),
                        s.getDurationMinutes()))
                .toList();
    }

    private String hostUser(CurrentUser me) {
        String configured = zoomProperties.hostEmail();
        return configured == null || configured.isBlank() ? me.email() : configured.strip();
    }

    private CurrentUser requireTeam() {
        CurrentUser me = currentUser.current();
        if (!me.is(Role.ADMIN)) {
            throw new AccessDeniedException("Only the Motor Movers team keeps the register");
        }
        return me;
    }

    private CohortSession find(Long id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("Live session", id));
    }

    private static TrainingViews.LiveSessionView view(CohortSession s, CurrentUser me, Instant now) {
        Cohort c = s.getCohort();
        boolean host = me.is(Role.ADMIN);
        return new TrainingViews.LiveSessionView(s.getId(), c.getId(), c.getCourse().getTitle(), c.getName(),
                s.getNumber(), c.getCourse().getLiveSessions(), s.getStartsAt(), s.getDurationMinutes(),
                c.getTrainerName(), host ? !s.isOverAt(now) : s.isOpenAt(now), host);
    }
}
