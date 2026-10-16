package uk.co.motormovers.platform.school.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.school.model.Trust;

public interface TrustRepository extends JpaRepository<Trust, Long> {
}
