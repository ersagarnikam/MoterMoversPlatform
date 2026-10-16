package uk.co.motormovers.platform.family.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.family.model.HomeSessionRecord;

public interface HomeSessionRecordRepository extends JpaRepository<HomeSessionRecord, Long> {

    boolean existsByClientId(String clientId);

    long countByParentIdAndStage(Long parentId, ColourStage stage);

    long countByParentId(Long parentId);

    List<HomeSessionRecord> findByParentIdAndDoneOnBetweenOrderByIdAsc(Long parentId, LocalDate from, LocalDate to);
}
