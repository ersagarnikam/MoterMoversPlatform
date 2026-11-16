package uk.co.motormovers.platform.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.ProgrammeFilm;

public interface ProgrammeFilmRepository extends JpaRepository<ProgrammeFilm, Long> {

    Optional<ProgrammeFilm> findByStageAndUnit(ColourStage stage, Integer unit);

    Optional<ProgrammeFilm> findByExerciseId(Long exerciseId);

    List<ProgrammeFilm> findByExerciseIdIn(Collection<Long> exerciseIds);

    List<ProgrammeFilm> findAllByOrderByStageAscUnitAscIdAsc();
}
