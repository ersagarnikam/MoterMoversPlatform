package uk.co.motormovers.platform.home.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import uk.co.motormovers.platform.home.model.Family;
import uk.co.motormovers.platform.home.model.HomeSubscription;

public interface HomeSubscriptionRepository extends JpaRepository<HomeSubscription, Long> {

    List<HomeSubscription> findByFamilyIdOrderById(Long familyId);

    /** Held until the transaction ends: Stripe's messages about one subscription take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HomeSubscription s where s.id = :id")
    Optional<HomeSubscription> findLockedById(@Param("id") Long id);

    Optional<HomeSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<HomeSubscription> findByStripeSessionId(String stripeSessionId);

    /**
     * Locks the family's row until the transaction ends, so two carers pressing Subscribe at once take turns and the
     * family never gets two subscriptions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Family f where f.id = :id")
    Optional<Family> lockFamily(@Param("id") Long id);
}
