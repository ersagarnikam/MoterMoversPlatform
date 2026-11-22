package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

import uk.co.motormovers.platform.home.model.Family;
import uk.co.motormovers.platform.home.model.FamilyMember;
import uk.co.motormovers.platform.home.model.HomeSubscription;
import uk.co.motormovers.platform.home.repository.FamilyMemberRepository;
import uk.co.motormovers.platform.home.repository.FamilyRepository;
import uk.co.motormovers.platform.home.repository.HomeSubscriptionRepository;
import uk.co.motormovers.platform.home.service.HomePassService;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.payment.service.OnlinePaymentService;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * A month of a family's subscription in the country a test context started in (task 68): the amount in that
 * country's currency with its tax, and a month in any other currency left alone. The country tests have no Stripe
 * keys, so the message goes straight to the code behind the webhook.
 */
@TestComponent
class MonthlyInRegion {

    @Autowired
    AppUserRepository users;

    @Autowired
    FamilyRepository families;

    @Autowired
    FamilyMemberRepository members;

    @Autowired
    HomeSubscriptionRepository subscriptions;

    @Autowired
    PaymentRepository payments;

    @Autowired
    OnlinePaymentService online;

    @Autowired
    HomePassService passes;

    @Autowired
    Clock clock;

    /** A family pays a month of {@code amount} in {@code currency}: the tax in it must be {@code tax}. */
    void aMonthIsPaidWithTax(String currency, String otherCurrency, int amount, int tax) {
        AppUser payer = users.save(new AppUser("monthly-" + UUID.randomUUID() + "@example.test", "A Parent", "x",
                Role.PARENT, null, null));
        Family family = families.save(new Family());
        members.save(new FamilyMember(family.getId(), payer.getId(), FamilyMember.Role.OWNER));
        long row = subscriptions.save(new HomeSubscription(family.getId(), payer.getId(), amount, clock.instant()))
                .getId();
        ZonedDateTime start = ZonedDateTime.now(clock);

        // Another country's currency: this deployment does not record it.
        assertThat(online.receive(StripeMessages.invoicePaid("in_other_" + row, row, "sub_" + row, "cus_" + row,
                amount, otherCurrency, start.toInstant(), start.plusMonths(1).toInstant()))).isFalse();
        assertThat(payments.findByPayerIdOrderByInvoicedOnDescIdDesc(payer.getId())).isEmpty();

        assertThat(online.receive(StripeMessages.invoicePaid("in_" + row, row, "sub_" + row, "cus_" + row, amount,
                currency, start.toInstant(), start.plusMonths(1).toInstant()))).isTrue();
        List<Payment> paid = payments.findByPayerIdOrderByInvoicedOnDescIdDesc(payer.getId());
        assertThat(paid).hasSize(1);
        assertThat(paid.getFirst().getAmountPence()).isEqualTo(amount);
        assertThat(paid.getFirst().taxPence()).isEqualTo(tax);
        // Covered from today, on this deployment's clock, to the day the next month is taken.
        assertThat(passes.running(family.getId())).isTrue();
        assertThat(passes.endsOn(family.getId())).isEqualTo(start.toLocalDate().plusMonths(1));
    }
}
