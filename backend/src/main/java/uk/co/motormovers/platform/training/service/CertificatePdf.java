package uk.co.motormovers.platform.training.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.Enrolment;

/** The practitioner certificate as a one-page A4 PDF, laid out like the printable page on the web. */
final class CertificatePdf {

    /** The seven colour stages, red to violet, as on the web certificate. */
    private static final List<Color> STAGES = List.of(new Color(0xd9534f), new Color(0xe8913a), new Color(0xe5c443),
            new Color(0x56a05a), new Color(0x4a7fc1), new Color(0x5b5ea6), new Color(0x9b6bb8));
    private static final Color INK = new Color(0x1f2a37);
    private static final Color MUTED = new Color(0x5b6675);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

    private CertificatePdf() {
    }

    static byte[] render(Enrolment e, boolean withQuizzes) {
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        Cohort cohort = e.getCohort();
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDRectangle a4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            PDPage page = new PDPage(a4);
            doc.addPage(page);
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Motor Movers certificate · " + e.getUser().getDisplayName());
            info.setAuthor("Motor Movers");
            float width = a4.getWidth();
            float height = a4.getHeight();
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                float band = width / STAGES.size();
                for (int i = 0; i < STAGES.size(); i++) {
                    c.setNonStrokingColor(STAGES.get(i));
                    c.addRect(i * band, height - 14, band, 14);
                    c.fill();
                }
                float y = height - 90;
                centred(c, bold, 12, MUTED, "MOTOR MOVERS", width, y);
                centred(c, bold, 32, INK, "Certificate of completion", width, y -= 50);
                centred(c, regular, 14, MUTED, "This certifies that", width, y -= 48);
                centred(c, bold, 28, INK, e.getUser().getDisplayName(), width, y -= 42);
                centred(c, regular, 14, MUTED, "has attended all " + cohort.getCourse().getLiveSessions()
                        + " live sessions" + (withQuizzes ? ", passed the quizzes" : "")
                        + " and passed the assessment for the", width, y -= 40);
                centred(c, bold, 20, INK, cohort.getCourse().getTitle(), width, y -= 36);
                centred(c, regular, 12, MUTED, cohort.getName(), width, y -= 24);
                centred(c, regular, 13, INK, "Issued " + date(e.getCertificateIssuedOn()) + "    ·    Valid until "
                        + date(e.getCertificateExpiresOn()), width, y -= 52);
                centred(c, regular, 10, MUTED, "Certificate MM-" + e.getId(), width, 48);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    private static void centred(PDPageContentStream c, PDType1Font font, float size, Color colour, String text,
            float pageWidth, float y) throws IOException {
        String safe = encodable(font, text);
        float textWidth = font.getStringWidth(safe) / 1000 * size;
        c.beginText();
        c.setFont(font, size);
        c.setNonStrokingColor(colour);
        c.newLineAtOffset((pageWidth - textWidth) / 2, y);
        c.showText(safe);
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
