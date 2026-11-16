package uk.co.motormovers.platform.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.UnitFilm;

public interface UnitFilmRepository extends JpaRepository<UnitFilm, Long> {

    Optional<UnitFilm> findByStageAndUnit(ColourStage stage, int unit);

    List<UnitFilm> findAllByOrderByStageAscUnitAsc();
}
