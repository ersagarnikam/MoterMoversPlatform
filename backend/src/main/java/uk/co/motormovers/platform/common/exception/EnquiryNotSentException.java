package uk.co.motormovers.platform.common.exception;

/** The email carrying a website enquiry failed. The message is for the visitor. */
public class EnquiryNotSentException extends RuntimeException {

    public EnquiryNotSentException() {
        super("Sorry, your enquiry could not be sent.");
    }
}
