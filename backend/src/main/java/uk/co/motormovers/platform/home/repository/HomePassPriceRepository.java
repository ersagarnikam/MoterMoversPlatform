package uk.co.motormovers.platform.home.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.home.model.HomePassPrice;

public interface HomePassPriceRepository extends JpaRepository<HomePassPrice, Long> {

    Optional<HomePassPrice> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate on);

    Optional<HomePassPrice> findByEffectiveFrom(LocalDate effectiveFrom);

    List<HomePassPrice> findAllByOrderByEffectiveFromDesc();
}
