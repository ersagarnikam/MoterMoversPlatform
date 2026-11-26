package uk.co.motormovers.platform.common.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " not found");
    }

    /** For something that is not there for a reason worth saying, in words safe to show. */
    public NotFoundException(String message) {
        super(message);
    }
}
