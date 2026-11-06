package uk.co.motormovers.platform.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import uk.co.motormovers.platform.payment.model.CreditNoteCounter;

public interface CreditNoteCounterRepository extends JpaRepository<CreditNoteCounter, Integer> {

    /** Held until the refund's transaction ends, so numbers stay in order with no gaps. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreditNoteCounter c where c.id = " + CreditNoteCounter.ROW)
    CreditNoteCounter locked();
}
