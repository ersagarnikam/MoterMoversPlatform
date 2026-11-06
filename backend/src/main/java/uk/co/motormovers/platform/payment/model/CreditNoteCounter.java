package uk.co.motormovers.platform.payment.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The last credit note number used in this deployment: one row, locked while the next number is taken. */
@Entity
@Table(name = "credit_note_counter")
public class CreditNoteCounter {

    public static final int ROW = 1;

    @Id
    private int id;

    private int lastNumber;

    protected CreditNoteCounter() {
    }

    public int next() {
        return ++lastNumber;
    }
}
