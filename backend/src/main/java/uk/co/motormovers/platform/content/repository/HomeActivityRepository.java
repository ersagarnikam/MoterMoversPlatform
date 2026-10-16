package uk.co.motormovers.platform.content.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.HomeActivity;

public interface HomeActivityRepository extends JpaRepository<HomeActivity, Long> {

    List<HomeActivity> findByStageAndStatusOrderByPosition(ColourStage stage, ContentStatus status);
}
