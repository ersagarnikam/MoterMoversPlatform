package uk.co.motormovers.platform.school;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenceRepository extends JpaRepository<Licence, Long> {

    /** The school's most recent licence. */
    Optional<Licence> findFirstBySchoolIdOrderByEndsOnDesc(Long schoolId);
}
