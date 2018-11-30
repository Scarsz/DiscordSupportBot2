package github.scarsz.discordsupportbot.exception;

import github.scarsz.discordsupportbot.support.Helpdesk;
import lombok.Getter;

public class HelpdeskDoesNotExistException extends Throwable {

    @Getter private final Helpdesk helpdesk;

    public HelpdeskDoesNotExistException(Helpdesk helpdesk) {
        this.helpdesk = helpdesk;
    }

}
