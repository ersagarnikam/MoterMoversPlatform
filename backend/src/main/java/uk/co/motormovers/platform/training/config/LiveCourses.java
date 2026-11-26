package uk.co.motormovers.platform.training.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import uk.co.motormovers.platform.common.exception.NotFoundException;

/**
 * Whether live courses run in this deployment (go-live task 89). Courses are recorded: a learner has a place on the
 * course itself and does its lessons in order. Live cohorts on Zoom, their registers, reminders and trainers are
 * kept in the code and the database, behind {@code platform.live-courses} ({@code LIVE_COURSES}), which is off
 * unless a deployment switches it on. While it is off, every live and trainer endpoint answers 404, the scheduled
 * reminders send nothing, and the screens hide them (the web and the app read {@code liveCourses} from
 * {@code GET /api/settings}).
 */
@Configuration
public class LiveCourses implements WebMvcConfigurer {

    /** The endpoints that exist only for live courses. */
    static final String[] LIVE_ONLY = {
        "/api/training/live/**", "/api/admin/live/**", "/api/training/cohorts/**", "/api/admin/cohorts/**",
        "/api/admin/courses/*/cohorts", "/api/admin/trainers/**", "/api/trainer/**",
        "/api/admin/enrolments/*/assessment",
    };

    private final boolean on;

    public LiveCourses(@Value("${platform.live-courses:false}") boolean on) {
        this.on = on;
    }

    public boolean on() {
        return on;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!on) {
                    throw new NotFoundException("Live courses are switched off: courses are recorded");
                }
                return true;
            }
        }).addPathPatterns(LIVE_ONLY);
    }
}
