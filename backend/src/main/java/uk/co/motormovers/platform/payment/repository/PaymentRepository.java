package uk.co.motormovers.platform.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import uk.co.motormovers.platform.payment.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"school", "school.trust", "payer"})
    List<Payment> findAllByOrderByInvoicedOnDescIdDesc();

    List<Payment> findBySchoolIdOrderByInvoicedOnDescIdDesc(Long schoolId);

    @EntityGraph(attributePaths = "school")
    List<Payment> findBySchoolIdIn(Collection<Long> schoolIds);

    List<Payment> findByPayerIdOrderByInvoicedOnDescIdDesc(Long payerId);

    /** Held until the transaction ends: a team refund and Stripe's webhook for it take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paidOnlineRef = :ref")
    Optional<Payment> findLockedByPaidOnlineRef(@Param("ref") String ref);
}
