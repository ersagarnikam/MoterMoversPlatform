package uk.co.motormovers.platform.enquiry.controller;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import uk.co.motormovers.platform.enquiry.dto.EnquiryRequest;
import uk.co.motormovers.platform.enquiry.service.EnquiryService;
import uk.co.motormovers.platform.security.service.RateLimiter;

/** The public website's enquiry form (price guide, applying). Open without signing in. */
@RestController
@RequestMapping("/api/enquiries")
public class EnquiryController {

    private final EnquiryService enquiries;
    private final RateLimiter rateLimiter;

    public EnquiryController(EnquiryService enquiries, RateLimiter rateLimiter) {
        this.enquiries = enquiries;
        this.rateLimiter = rateLimiter;
    }

    /** Five enquiries an hour from one network address. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@Valid @RequestBody EnquiryRequest request, HttpServletRequest http) {
        rateLimiter.check("enquiry-ip", http.getRemoteAddr(), 5, Duration.ofHours(1));
        enquiries.send(request);
    }
}
