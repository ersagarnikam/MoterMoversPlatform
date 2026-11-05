package uk.co.motormovers.platform.school.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.school.model.Licence;

public interface LicenceRepository extends JpaRepository<Licence, Long> {

    /** The school's most recent licence, for showing its status. Whether a stage is open: {@code LicenceService}. */
    Optional<Licence> findFirstBySchoolIdOrderByEndsOnDesc(Long schoolId);

    List<Licence> findBySchoolId(Long schoolId);

    long countByPlanCode(String planCode);
}
