package uk.co.motormovers.platform.school.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.repository.LicenceRepository;

/**
 * The one rule for what a school's licences open: a colour stage is open on a day when any of the school's
 * licences runs that day and covers the stage. Sessions (shown and recorded) and the resource library use it.
 * Screens that show "the school's licence" (status, plan, end date) still show the latest one.
 */
@Service
public class LicenceService {

    private final LicenceRepository licences;

    public LicenceService(LicenceRepository licences) {
        this.licences = licences;
    }

    @Transactional(readOnly = true)
    public boolean licensedFor(Long schoolId, ColourStage stage, LocalDate day) {
        return licences.findBySchoolId(schoolId).stream().anyMatch(l -> l.isValidOn(day) && l.covers(stage));
    }

    /** Every stage open in the school on the day, across all its licences. */
    @Transactional(readOnly = true)
    public Set<ColourStage> licensedStages(Long schoolId, LocalDate day) {
        Set<ColourStage> stages = EnumSet.noneOf(ColourStage.class);
        licences.findBySchoolId(schoolId).stream()
                .filter(l -> l.isValidOn(day))
                .map(Licence::getStages)
                .forEach(stages::addAll);
        return stages;
    }
}
