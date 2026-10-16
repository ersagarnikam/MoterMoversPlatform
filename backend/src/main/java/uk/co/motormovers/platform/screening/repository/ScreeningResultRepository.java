package uk.co.motormovers.platform.screening.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import uk.co.motormovers.platform.screening.model.ScreeningResult;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long> {

    boolean existsByClientId(String clientId);

    List<ScreeningResult> findByChildIdOrderByRoundNumberAscScreenedOnAsc(Long childId);

    @Query("select distinct r.child.id from ScreeningResult r where r.child.id in :childIds")
    List<Long> findScreenedChildIds(Collection<Long> childIds);
}
