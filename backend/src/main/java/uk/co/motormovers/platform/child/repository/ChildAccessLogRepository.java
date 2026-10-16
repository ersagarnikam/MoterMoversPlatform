package uk.co.motormovers.platform.child.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.child.model.ChildAccessLog;

public interface ChildAccessLogRepository extends JpaRepository<ChildAccessLog, Long> {

    List<ChildAccessLog> findByChildIdOrderByAtDesc(Long childId);

    List<ChildAccessLog> findTop200BySchoolIdOrderByAtDesc(Long schoolId);
}
