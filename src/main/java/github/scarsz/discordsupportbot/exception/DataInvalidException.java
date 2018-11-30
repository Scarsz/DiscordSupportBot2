package github.scarsz.discordsupportbot.exception;

import github.scarsz.discordsupportbot.support.Helpdesk;
import lombok.Getter;

public class DataInvalidException extends Throwable {

    @Getter private final Helpdesk helpdesk;
    @Getter private final String reason;
    @Getter private final String value;

    public DataInvalidException(Helpdesk helpdesk, String reason, String value) {
        this.helpdesk = helpdesk;
        this.reason = reason;
        this.value = value;
    }

}
