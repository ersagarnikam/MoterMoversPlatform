package uk.co.motormovers.platform.home.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.home.model.HomeMonthlyPrice;

public interface HomeMonthlyPriceRepository extends JpaRepository<HomeMonthlyPrice, Long> {

    Optional<HomeMonthlyPrice> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate day);

    Optional<HomeMonthlyPrice> findByEffectiveFrom(LocalDate day);

    List<HomeMonthlyPrice> findAllByOrderByEffectiveFromDesc();
}
