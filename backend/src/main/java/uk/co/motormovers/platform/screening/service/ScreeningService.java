package uk.co.motormovers.platform.screening.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.model.ChildAccessLog;
import uk.co.motormovers.platform.child.repository.ChildAccessLogRepository;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.screening.dto.ScreeningViews;
import uk.co.motormovers.platform.screening.model.MotorArea;
import uk.co.motormovers.platform.screening.model.ReadinessBand;
import uk.co.motormovers.platform.screening.model.ScreeningItem;
import uk.co.motormovers.platform.screening.model.ScreeningResult;
import uk.co.motormovers.platform.screening.repository.ScreeningItemRepository;
import uk.co.motormovers.platform.screening.repository.ScreeningResultRepository;
import uk.co.motormovers.platform.screening.util.ReadinessRules;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;

@Service
public class ScreeningService {

    /** Scores more than this many days after a round began start a new round (e.g. end-of-year re-screen). */
    static final int NEW_ROUND_AFTER_DAYS = 60;

    private final ScreeningItemRepository items;
    private final ScreeningResultRepository results;
    private final ChildRepository children;
    private final ChildAccessLogRepository accessLog;
    private final AccessPolicy access;

    public ScreeningService(ScreeningItemRepository items, ScreeningResultRepository results,
            ChildRepository children, ChildAccessLogRepository accessLog, AccessPolicy access) {
        this.items = items;
        this.results = results;
        this.children = children;
        this.accessLog = accessLog;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<ScreeningViews.Item> items() {
        return items.findAllByOrderByPosition().stream().map(ScreeningViews.Item::of).toList();
    }

    /** Accepts a batch of scores, typically queued on a tablet while offline. Safe to resend. */
    @Transactional
    public ScreeningViews.SubmitResult submit(List<ScreeningViews.Score> scores) {
        Map<String, ScreeningItem> itemsByCode = items.findAll().stream()
                .collect(Collectors.toMap(ScreeningItem::getCode, i -> i));
        int saved = 0;
        int duplicates = 0;
        Map<Long, Child> touched = new TreeMap<>();
        for (ScreeningViews.Score s : scores) {
            if (results.existsByClientId(s.clientId())) {
                duplicates++;
                continue;
            }
            Child child = children.findById(s.childId()).orElseThrow(() -> new NotFoundException("Child", s.childId()));
            CurrentUser me = access.requireClassAccess(child.getSchoolClass());
            ScreeningItem item = itemsByCode.get(s.itemCode());
            if (item == null) {
                throw new IllegalArgumentException("Unknown screening item " + s.itemCode());
            }
            if (s.score() < 0 || s.score() > ScreeningItem.MAX_SCORE) {
                throw new IllegalArgumentException("Score must be between 0 and " + ScreeningItem.MAX_SCORE);
            }
            int round = roundFor(child.getId(), s.screenedOn());
            results.save(new ScreeningResult(s.clientId(), child, item, round, s.score(), s.screenedOn(), me.email()));
            touched.put(child.getId(), child);
            saved++;
        }
        results.flush();
        touched.values().forEach(this::updatePlacement);
        return new ScreeningViews.SubmitResult(saved, duplicates);
    }

    /** A child's readiness profile. Every view is logged. */
    @Transactional
    public ScreeningViews.Profile profile(Long childId) {
        Child child = children.findById(childId).orElseThrow(() -> new NotFoundException("Child", childId));
        CurrentUser me = access.requireClassAccess(child.getSchoolClass());
        accessLog.save(new ChildAccessLog(child.getId(), child.getSchoolClass().getSchool().getId(), me.email(),
                "VIEW_PROFILE"));

        List<ScreeningResult> all = results.findByChildIdOrderByRoundNumberAscScreenedOnAsc(childId);
        List<ScreeningViews.AreaResult> areas = new ArrayList<>();
        List<ScreeningViews.RoundPoint> history = new ArrayList<>();
        ColourStage suggested = null;
        if (!all.isEmpty()) {
            int latest = all.getLast().getRoundNumber();
            Map<MotorArea, List<Integer>> byArea = new EnumMap<>(MotorArea.class);
            all.stream().filter(r -> r.getRoundNumber() == latest)
                    .forEach(r -> byArea.computeIfAbsent(r.getItem().getArea(), a -> new ArrayList<>()).add(r.getScore()));
            byArea.forEach((area, scores) -> {
                double f = ReadinessRules.fraction(scores);
                ReadinessBand band = ReadinessRules.band(f);
                areas.add(new ScreeningViews.AreaResult(area, area.label(), f, band, band.label()));
            });
            all.stream().collect(Collectors.groupingBy(ScreeningResult::getRoundNumber, TreeMap::new, Collectors.toList()))
                    .forEach((round, rs) -> history.add(new ScreeningViews.RoundPoint(round,
                            rs.stream().map(ScreeningResult::getScreenedOn).min(Comparator.naturalOrder()).orElseThrow(),
                            ReadinessRules.fraction(rs.stream().map(ScreeningResult::getScore).toList()))));
            suggested = ReadinessRules.suggestedStage(history.getLast().overallFraction());
        }
        return new ScreeningViews.Profile(child.getId(), "Child " + child.getId(), child.displayName(),
                child.getSchoolClass().getName(), ScreeningViews.NOT_A_DIAGNOSIS, !all.isEmpty(), suggested,
                suggested == null ? null : suggested.displayName(), child.getCurrentStage(), areas, history);
    }

    private int roundFor(Long childId, LocalDate screenedOn) {
        List<ScreeningResult> existing = results.findByChildIdOrderByRoundNumberAscScreenedOnAsc(childId);
        if (existing.isEmpty()) {
            return 1;
        }
        int latest = existing.getLast().getRoundNumber();
        LocalDate roundStart = existing.stream().filter(r -> r.getRoundNumber() == latest)
                .map(ScreeningResult::getScreenedOn).min(Comparator.naturalOrder()).orElseThrow();
        return ChronoUnit.DAYS.between(roundStart, screenedOn) > NEW_ROUND_AFTER_DAYS ? latest + 1 : latest;
    }

    /** Places the child at the suggested stage the first time, and moves them up (never down) after re-screening. */
    private void updatePlacement(Child child) {
        List<ScreeningResult> all = results.findByChildIdOrderByRoundNumberAscScreenedOnAsc(child.getId());
        int latest = all.getLast().getRoundNumber();
        double overall = ReadinessRules.fraction(all.stream().filter(r -> r.getRoundNumber() == latest)
                .map(ScreeningResult::getScore).toList());
        ColourStage suggested = ReadinessRules.suggestedStage(overall);
        if (child.getCurrentStage() == null || suggested.compareTo(child.getCurrentStage()) > 0) {
            child.placeAt(suggested);
        }
    }
}
