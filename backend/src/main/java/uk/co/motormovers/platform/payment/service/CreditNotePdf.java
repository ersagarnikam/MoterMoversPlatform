package uk.co.motormovers.platform.payment.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import uk.co.motormovers.platform.common.util.Money;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentRefund;

/**
 * A credit note as a one-page A4 PDF. Its title comes from the country settings, and the accountant still has to confirm
 * the wording a credit note needs (go-live task 17). A payment with no saved tax rate shows no tax line.
 */
final class CreditNotePdf {

    private static final Color INK = new Color(0x1f2a37);
    private static final Color MUTED = new Color(0x5b6675);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final float LEFT = 56;

    private CreditNotePdf() {
    }

    static byte[] render(PaymentRefund refund, String title, String taxLabel, String legalLine, Money money) {
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        Payment payment = refund.getPayment();
        String payer = payment.getSchool() != null ? payment.getSchool().getName()
                : payment.getPayer().getDisplayName();
        String reference = payment.getReference() != null ? payment.getReference() : "Payment " + payment.getId();
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            doc.getDocumentInformation().setTitle(title + " " + refund.creditNoteLabel());
            doc.getDocumentInformation().setAuthor("Motor Movers");
            float y = PDRectangle.A4.getHeight() - 80;
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                text(c, bold, 12, MUTED, "MOTOR MOVERS", y);
                text(c, bold, 26, INK, title, y -= 40);
                text(c, regular, 12, INK, refund.creditNoteLabel(), y -= 28);
                text(c, regular, 12, MUTED, "Date: " + DATE.format(refund.getRefundedOn()), y -= 18);
                text(c, bold, 12, INK, "To", y -= 40);
                text(c, regular, 12, INK, payer, y -= 18);
                text(c, bold, 12, INK, "For", y -= 32);
                text(c, regular, 12, INK, payment.getDescription(), y -= 18);
                text(c, regular, 12, MUTED, "Original payment: " + reference, y -= 18);
                text(c, regular, 12, MUTED, "Reason: " + refund.getReason(), y -= 18);
                text(c, bold, 16, INK, "Amount refunded: " + money.format(refund.getAmountPence()), y -= 44);
                if (refund.getTaxPence() != null) {
                    text(c, regular, 12, MUTED, "Includes " + taxLabel + " " + money.format(refund.getTaxPence()),
                            y -= 20);
                }
                if (legalLine != null) {
                    text(c, regular, 8, MUTED, legalLine, 48);
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void text(PDPageContentStream c, PDType1Font font, float size, Color colour, String text, float y)
            throws IOException {
        c.beginText();
        c.setFont(font, size);
        c.setNonStrokingColor(colour);
        c.newLineAtOffset(LEFT, y);
        c.showText(encodable(font, text));
        c.endText();
    }

    /** The standard PDF fonts cover Western European letters; anything else (rare in names) shows as "?". */
    private static String encodable(PDType1Font font, String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                out.append(ch);
            } catch (IOException | IllegalArgumentException ex) {
                out.append('?');
            }
        });
        return out.toString();
    }
}
