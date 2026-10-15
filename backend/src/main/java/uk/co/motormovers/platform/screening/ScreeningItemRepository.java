package uk.co.motormovers.platform.screening;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningItemRepository extends JpaRepository<ScreeningItem, Long> {

    List<ScreeningItem> findAllByOrderByPosition();

    Optional<ScreeningItem> findByCode(String code);
}
