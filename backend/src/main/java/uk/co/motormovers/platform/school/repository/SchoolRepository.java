package uk.co.motormovers.platform.school.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.school.model.School;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findByTrustIdOrderByName(Long trustId);
}
