package uk.co.motormovers.platform.home.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.home.dto.HomeViews;

/**
 * The cool-down that ends a home session (src/main/resources/assessment/cool-downs.json). Pat (2026-11-05): from Green
 * to Indigo each block of exercises has its cool-down; Red, Orange and Yellow end each rhyme with their own, already in
 * the rhyme's instructions. A module without its own cool-down yet uses the colour's latest one before it.
 */
@Component
public class CoolDowns {

    /** The colours whose sessions end with a separate cool-down. */
    static final Set<ColourStage> WITH_COOL_DOWNS = EnumSet.of(ColourStage.GREEN, ColourStage.BLUE,
            ColourStage.INDIGO);

    record CoolDownFile(String source, List<Entry> coolDowns) {
    }

    record Entry(ColourStage stage, int module, String title, String text) {
    }

    private final List<Entry> entries;

    public CoolDowns(JsonMapper json) {
        try (InputStream in = new ClassPathResource("assessment/cool-downs.json").getInputStream()) {
            this.entries = json.readValue(in, CoolDownFile.class).coolDowns();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read assessment/cool-downs.json", e);
        }
    }

    /** The cool-down for a module of a colour, or null when there is none (a rhyme colour, or none sent yet). */
    public HomeViews.CoolDown forModule(ColourStage stage, int module) {
        if (!WITH_COOL_DOWNS.contains(stage)) {
            return null;
        }
        return entries.stream()
                .filter(e -> e.stage() == stage && e.module() <= module)
                .max(Comparator.comparingInt(Entry::module))
                .map(e -> new HomeViews.CoolDown(e.title(), e.text()))
                .orElse(null);
    }

    /** Every text, for the wording check. */
    public List<String> texts() {
        return entries.stream().flatMap(e -> Stream.of(e.title(), e.text())).toList();
    }
}
