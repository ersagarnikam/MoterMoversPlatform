package uk.co.motormovers.platform.screening.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.screening.model.ScreeningItem;

public interface ScreeningItemRepository extends JpaRepository<ScreeningItem, Long> {

    List<ScreeningItem> findAllByOrderByPosition();

    Optional<ScreeningItem> findByCode(String code);
}
