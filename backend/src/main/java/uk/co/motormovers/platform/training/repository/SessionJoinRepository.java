package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.SessionAttendance;
import uk.co.motormovers.platform.training.model.SessionJoin;

public interface SessionJoinRepository extends JpaRepository<SessionJoin, SessionAttendance.Key> {

    List<SessionJoin> findByIdSessionId(Long sessionId);
}
