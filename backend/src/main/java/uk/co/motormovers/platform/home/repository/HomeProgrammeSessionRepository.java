package uk.co.motormovers.platform.home.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import uk.co.motormovers.platform.home.model.HomeProgrammeSession;
import uk.co.motormovers.platform.common.model.ColourStage;

public interface HomeProgrammeSessionRepository extends JpaRepository<HomeProgrammeSession, Long> {

    boolean existsByClientId(String clientId);

    long countByChildIdAndStage(Long childId, ColourStage stage);

    long countByChildId(Long childId);

    List<HomeProgrammeSession> findByChildIdAndDoneOnBetween(Long childId, LocalDate from, LocalDate to);

    @Modifying
    @Query("update HomeProgrammeSession s set s.childId = :to where s.childId = :from")
    int moveSessions(Long from, Long to);
}
