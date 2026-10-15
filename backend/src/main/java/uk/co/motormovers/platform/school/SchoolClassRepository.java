package uk.co.motormovers.platform.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findBySchoolIdOrderByName(Long schoolId);

    List<SchoolClass> findByTeacherIdOrderByName(Long teacherId);
}
