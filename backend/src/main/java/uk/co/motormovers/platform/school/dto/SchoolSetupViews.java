package uk.co.motormovers.platform.school.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;

/** Request and response shapes for setting a school up: staff, classes, children and privacy settings. */
public final class SchoolSetupViews {

    private SchoolSetupViews() {
    }

    public record Settings(boolean fullNamesEnabled, boolean videoEnabled, boolean trustCanSeeChildren) {

        public static Settings of(School s) {
            return new Settings(s.isFullNamesEnabled(), s.isVideoEnabled(), s.isTrustCanSeeChildren());
        }
    }

    public record StaffMember(Long id, String email, String displayName, Role role, boolean enabled,
            LocalDate inductionCompletedOn) {

        public static StaffMember of(AppUser u) {
            return new StaffMember(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isEnabled(),
                    u.getInductionCompletedOn());
        }
    }

    public record ClassRow(Long id, String name, String yearGroup, ColourStage stage, Long teacherId,
            String teacherName, int children) {

        public static ClassRow of(SchoolClass c, int children) {
            return new ClassRow(c.getId(), c.getName(), c.getYearGroup(), c.getStage(),
                    c.getTeacher() == null ? null : c.getTeacher().getId(),
                    c.getTeacher() == null ? null : c.getTeacher().getDisplayName(), children);
        }
    }

    public record ChildRow(Long id, String initials, String fullName, String yearGroup, ColourStage currentStage) {

        public static ChildRow of(Child c) {
            return new ChildRow(c.getId(), c.getInitials(), c.getFullName(), c.getYearGroup(), c.getCurrentStage());
        }
    }

    public record Setup(Long schoolId, String schoolName, Settings settings, List<StaffMember> staff,
            List<ClassRow> classes) {
    }

    public record NewStaff(@NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(max = 100) String displayName, @NotNull Role role) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record ClassRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 30) String yearGroup,
            @NotNull ColourStage stage, Long teacherId) {
    }

    public record NewChild(@NotBlank @Size(max = 10) String initials, @Size(max = 200) String fullName) {
    }

    public record NewChildren(@NotEmpty @Size(max = 60) List<@Valid NewChild> children) {
    }

    public record LeftRequest(@NotNull LocalDate leftOn) {
    }

    public record MoveRequest(@NotNull Long classId) {
    }
}
