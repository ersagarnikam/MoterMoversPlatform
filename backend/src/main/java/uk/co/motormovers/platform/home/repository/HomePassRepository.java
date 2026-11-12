package uk.co.motormovers.platform.home.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.home.model.HomePass;

public interface HomePassRepository extends JpaRepository<HomePass, Long> {

    List<HomePass> findByFamilyIdOrderById(Long familyId);

    Optional<HomePass> findByPaymentId(Long paymentId);

    List<HomePass> findByStartsOnIsNotNull();
}
