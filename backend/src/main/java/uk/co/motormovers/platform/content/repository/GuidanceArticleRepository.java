package uk.co.motormovers.platform.content.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.GuidanceArticle;

public interface GuidanceArticleRepository extends JpaRepository<GuidanceArticle, Long> {

    List<GuidanceArticle> findByStatusOrderByIdDesc(ContentStatus status);

    List<GuidanceArticle> findAllByOrderByIdDesc();

    long countByStatus(ContentStatus status);
}
