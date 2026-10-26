package uk.co.motormovers.platform.assessment.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import uk.co.motormovers.platform.assessment.model.Chart;
import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneRepository;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * Loads Pat's developmental charts and exercise grids (src/main/resources/assessment, made from her documents of
 * 2026-10-27) on start-up. It adds only what is missing: the charts on an empty platform, and the exercises of a
 * colour that has none yet, so a new colour's grid can be added as a file later without touching the others.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AssessmentContent implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AssessmentContent.class);

    /** The colours with a grid so far. Pat is to send the other five. */
    static final List<ColourStage> STAGES_WITH_EXERCISES = List.of(ColourStage.YELLOW, ColourStage.GREEN);

    record MilestoneFile(String source, List<MilestoneRow> fine, List<MilestoneRow> gross) {
    }

    record MilestoneRow(String band, int fromMonths, int toMonths, String description) {
    }

    record ExerciseFile(String source, ColourStage stage, List<ExerciseRow> exercises) {
    }

    record ExerciseRow(int number, String title, String instructions, List<String> levels, List<String> points) {
    }

    private final MilestoneRepository milestones;
    private final ExerciseRepository exercises;
    private final JsonMapper json;

    public AssessmentContent(MilestoneRepository milestones, ExerciseRepository exercises, JsonMapper json) {
        this.milestones = milestones;
        this.exercises = exercises;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        loadMissing();
    }

    @Transactional
    public void loadMissing() {
        if (milestones.count() == 0) {
            MilestoneFile file = read("assessment/milestones.json", MilestoneFile.class);
            List<Milestone> rows = new ArrayList<>();
            addChart(rows, Chart.FINE, file.fine());
            addChart(rows, Chart.GROSS, file.gross());
            milestones.saveAll(rows);
            log.info("Assessment: loaded {} developmental milestones", rows.size());
        }
        for (ColourStage stage : STAGES_WITH_EXERCISES) {
            if (exercises.existsByStage(stage)) {
                continue;
            }
            ExerciseFile file = read("assessment/exercises-" + stage.name().toLowerCase(Locale.ROOT)
                    + ".json", ExerciseFile.class);
            if (file.stage() != stage) {
                throw new IllegalStateException("The " + stage.label() + " exercise file is for " + file.stage());
            }
            exercises.saveAll(file.exercises().stream().map(e -> exercise(stage, e)).toList());
            log.info("Assessment: loaded {} {} exercises", file.exercises().size(), stage.label());
        }
    }

    private static void addChart(List<Milestone> rows, Chart chart, List<MilestoneRow> file) {
        int position = 1;
        for (MilestoneRow r : file) {
            rows.add(new Milestone(chart, position++, r.band(), r.fromMonths(), r.toMonths(), r.description()));
        }
    }

    private static Exercise exercise(ColourStage stage, ExerciseRow row) {
        Map<Integer, String> levels = new TreeMap<>();
        for (int i = 0; i < row.levels().size(); i++) {
            String text = row.levels().get(i);
            if (text != null && !text.isBlank()) {
                levels.put(i + 1, text);
            }
        }
        row.points().forEach(p -> {
            if (!ExercisePoints.valid(p)) {
                throw new IllegalStateException(stage.label() + " exercise " + row.number() + ": unknown point " + p);
            }
        });
        return new Exercise(stage, row.number(), row.title(), row.instructions(), levels, row.points());
    }

    private <T> T read(String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
