package iwkms.roomflow.exception;

public class HolidayUnavailableException extends RuntimeException {
    public HolidayUnavailableException(Throwable cause) {
        super("Holiday calendar is temporarily unavailable. Please try again later.", cause);
    }
}
