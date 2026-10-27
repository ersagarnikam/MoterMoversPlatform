package uk.co.motormovers.platform.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.home.model.Family;

public interface FamilyRepository extends JpaRepository<Family, Long> {
}
