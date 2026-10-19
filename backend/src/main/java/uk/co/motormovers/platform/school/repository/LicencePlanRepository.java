package uk.co.motormovers.platform.school.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.school.model.LicencePlan;

public interface LicencePlanRepository extends JpaRepository<LicencePlan, String> {

    List<LicencePlan> findAllByOrderBySortOrderAscLabelAsc();
}
