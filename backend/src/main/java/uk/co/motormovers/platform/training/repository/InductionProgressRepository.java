package uk.co.motormovers.platform.training.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.training.model.InductionProgress;

public interface InductionProgressRepository extends JpaRepository<InductionProgress, InductionProgress.Key> {

    List<InductionProgress> findByIdUserId(Long userId);
}
