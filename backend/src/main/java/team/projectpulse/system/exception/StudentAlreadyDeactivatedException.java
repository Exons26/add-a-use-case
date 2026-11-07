package team.projectpulse.system.exception;

/**
 * Deactivating a student whose account is already deactivated (UC-STU-deactivate-student extension 3a). Mapped to
 * 409 so a stale screen tells the actor the truth rather than reporting a change that did not happen.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException(Integer studentId) {
        super("Student " + studentId + " is already deactivated.");
    }

}
