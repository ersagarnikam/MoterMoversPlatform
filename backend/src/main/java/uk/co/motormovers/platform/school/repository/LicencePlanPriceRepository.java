package uk.co.motormovers.platform.school.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.school.model.LicencePlanPrice;

public interface LicencePlanPriceRepository extends JpaRepository<LicencePlanPrice, Long> {

    /** The price in force on a date: the latest change that started on or before it. */
    Optional<LicencePlanPrice> findFirstByPlanCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(String planCode,
            LocalDate on);

    Optional<LicencePlanPrice> findByPlanCodeAndEffectiveFrom(String planCode, LocalDate effectiveFrom);

    List<LicencePlanPrice> findByPlanCodeOrderByEffectiveFromDesc(String planCode);
}
