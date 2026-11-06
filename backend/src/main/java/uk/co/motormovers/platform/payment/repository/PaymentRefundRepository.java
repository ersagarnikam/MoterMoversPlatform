package uk.co.motormovers.platform.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.payment.model.PaymentRefund;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByStripeRefundId(String stripeRefundId);

    Optional<PaymentRefund> findByClientId(String clientId);
}
