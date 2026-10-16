package uk.co.motormovers.platform.school.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.school.model.SchoolClass;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findBySchoolIdOrderByName(Long schoolId);

    List<SchoolClass> findByTeacherIdOrderByName(Long teacherId);
}
