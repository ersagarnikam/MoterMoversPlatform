package uk.co.motormovers.platform.assessment.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.LibraryResource;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.model.ResourceKind;
import uk.co.motormovers.platform.content.repository.LibraryResourceRepository;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;

/**
 * Loads Pat's daily session plans (src/main/resources/assessment/session-plan-*.json) on start-up, in every deployment,
 * as drafts for the team to approve. It adds only the sessions that are missing and never changes one already there.
 * Each week of a file is five sessions, and the number of weeks comes from the file. A session keeps the grid's name of
 * each exercise; the teacher's screen takes the instructions from the grid. A name with no match in that colour's grid
 * stops the start-up, so a plan can never lose an exercise silently. Runs after {@link AssessmentContent}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SessionPlans implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SessionPlans.class);

    public static final int SESSIONS_A_WEEK = 5;

    private static final int MINUTES = 10;

    private static final String RHYME = "Rhyme";

    private static final Set<ColourStage> RHYME_COLOURS = EnumSet.of(ColourStage.RED, ColourStage.ORANGE,
            ColourStage.YELLOW);

    /** unitName is "Module", or "Rhyme" where each unit is one rhyme. */
    public record PlanFile(String source, ColourStage stage, String unitName, List<PlanUnit> units) {
    }

    public record PlanUnit(int number, List<List<String>> weeks) {
    }

    private record Slot(int unit, int day) {
    }

    private final ProgrammeSessionRepository sessions;
    private final ExerciseRepository exercises;
    private final LibraryResourceRepository resources;
    private final JsonMapper json;

    public SessionPlans(ProgrammeSessionRepository sessions, ExerciseRepository exercises,
            LibraryResourceRepository resources, JsonMapper json) {
        this.sessions = sessions;
        this.exercises = exercises;
        this.resources = resources;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        loadMissing();
    }

    public void loadMissing() {
        for (ColourStage stage : ColourStage.values()) {
            PlanFile file = read(stage);
            if (file.stage() != stage) {
                throw new IllegalStateException("The " + stage.label() + " session plan is for " + file.stage());
            }
            add(file);
        }
        linkRhymes();
    }

    /** Saves the sessions of this plan that are not there yet, as drafts, and returns how many. */
    public int add(PlanFile file) {
        Set<Slot> existing = sessions.findByStage(file.stage()).stream()
                .map(s -> new Slot(s.getUnit(), s.getDay()))
                .collect(Collectors.toCollection(HashSet::new));
        List<ProgrammeSession> missing = build(file).stream()
                .filter(s -> !existing.contains(new Slot(s.getUnit(), s.getDay())))
                .toList();
        if (!missing.isEmpty()) {
            sessions.saveAll(missing);
            log.info("Session plans: added {} {} sessions as drafts", missing.size(), file.stage().label());
        }
        return missing.size();
    }

    /**
     * Links each rhyme session with no rhyme yet to the library's rhyme of that name, the same colour's first. Runs on
     * every start and after starter content is loaded, so a rhyme added later is picked up.
     */
    public void linkRhymes() {
        List<LibraryResource> rhymes = resources.findAll().stream()
                .filter(r -> r.getKind() == ResourceKind.RHYME)
                .toList();
        if (rhymes.isEmpty()) {
            return;
        }
        List<ProgrammeSession> linked = new ArrayList<>();
        for (ColourStage stage : RHYME_COLOURS) {
            for (ProgrammeSession session : sessions.findByStage(stage)) {
                if (session.getRhyme() == null && session.getActivities().size() == 1) {
                    rhymeNamed(rhymes, session.getActivities().getFirst(), stage).ifPresent(rhyme -> {
                        session.setRhyme(rhyme);
                        linked.add(session);
                    });
                }
            }
        }
        if (!linked.isEmpty()) {
            sessions.saveAll(linked);
            log.info("Session plans: linked {} sessions to their library rhymes", linked.size());
        }
    }

    private static Optional<LibraryResource> rhymeNamed(List<LibraryResource> rhymes, String name, ColourStage stage) {
        List<LibraryResource> named = rhymes.stream().filter(r -> comparable(r.getTitle()).equals(comparable(name)))
                .toList();
        return named.stream().filter(r -> r.getStage() == stage).findFirst().or(() -> named.stream().findFirst());
    }

    private static String comparable(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private List<ProgrammeSession> build(PlanFile file) {
        Set<String> grid = exercises.findByStageOrderByNumber(file.stage()).stream()
                .map(Exercise::getTitle)
                .collect(Collectors.toSet());
        List<ProgrammeSession> built = new ArrayList<>();
        for (PlanUnit unit : file.units()) {
            int day = 0;
            List<String> before = List.of();
            for (int week = 1; week <= unit.weeks().size(); week++) {
                List<String> names = unit.weeks().get(week - 1);
                for (String name : names) {
                    if (!grid.contains(name)) {
                        throw new IllegalStateException("The " + file.stage().label() + " session plan names \"" + name
                                + "\", which is not in that colour's grid");
                    }
                }
                String focus = focus(file, names, before, week, unit.weeks().size());
                for (int d = 1; d <= SESSIONS_A_WEEK; d++) {
                    built.add(new ProgrammeSession(file.stage(), unit.number(), ++day, title(file, unit, week, d),
                            focus, MINUTES, null, names));
                }
                before = names;
            }
        }
        return built;
    }

    private static String title(PlanFile file, PlanUnit unit, int week, int day) {
        String start = file.stage().label() + " " + file.unitName() + " " + unit.number() + ", ";
        return RHYME.equals(file.unitName()) ? start + "Day " + day : start + "Week " + week + ", Day " + day;
    }

    private static String focus(PlanFile file, List<String> names, List<String> before, int week, int weeks) {
        if (RHYME.equals(file.unitName())) {
            return "This week's rhyme: " + String.join(", ", names) + ".";
        }
        String position = "Week " + week + " of " + weeks + ".";
        List<String> added = names.stream().filter(n -> !before.contains(n)).toList();
        return week == 1 || added.isEmpty() ? position : position + " New this week: " + String.join(", ", added) + ".";
    }

    private PlanFile read(ColourStage stage) {
        String path = "assessment/session-plan-" + stage.name().toLowerCase(Locale.ROOT) + ".json";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, PlanFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
