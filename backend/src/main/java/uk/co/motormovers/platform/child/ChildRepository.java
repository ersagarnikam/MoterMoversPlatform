package uk.co.motormovers.platform.child;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findBySchoolClassIdAndLeftOnIsNullOrderByInitials(Long schoolClassId);

    List<Child> findBySchoolClassSchoolIdAndLeftOnIsNull(Long schoolId);

    List<Child> findByLeftOnBefore(LocalDate cutoff);
}
