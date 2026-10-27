package uk.co.motormovers.platform.assessment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.assessment.model.Milestone;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findAllByOrderByChartAscPositionAsc();
}
