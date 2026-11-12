package uk.co.motormovers.platform.home.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.home.model.HomePassReminder;

public interface HomePassReminderRepository extends JpaRepository<HomePassReminder, Long> {

    boolean existsByFamilyIdAndEndsOnAndReminderDays(Long familyId, LocalDate endsOn, int reminderDays);
}
