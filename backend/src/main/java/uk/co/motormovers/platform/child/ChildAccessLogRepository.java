package uk.co.motormovers.platform.child;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildAccessLogRepository extends JpaRepository<ChildAccessLog, Long> {

    List<ChildAccessLog> findByChildIdOrderByAtDesc(Long childId);

    List<ChildAccessLog> findTop200BySchoolIdOrderByAtDesc(Long schoolId);
}
