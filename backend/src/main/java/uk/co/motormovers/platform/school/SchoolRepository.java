package uk.co.motormovers.platform.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findByTrustIdOrderByName(Long trustId);
}
