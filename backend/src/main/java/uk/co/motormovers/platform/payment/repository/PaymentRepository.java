package uk.co.motormovers.platform.payment.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.payment.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"school", "school.trust", "payer"})
    List<Payment> findAllByOrderByInvoicedOnDescIdDesc();

    List<Payment> findBySchoolIdOrderByInvoicedOnDescIdDesc(Long schoolId);

    @EntityGraph(attributePaths = "school")
    List<Payment> findBySchoolIdIn(Collection<Long> schoolIds);

    List<Payment> findByPayerIdOrderByInvoicedOnDescIdDesc(Long payerId);
}
