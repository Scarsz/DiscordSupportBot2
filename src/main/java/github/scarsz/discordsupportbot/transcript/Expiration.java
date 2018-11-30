package github.scarsz.discordsupportbot.transcript;

import java.util.concurrent.TimeUnit;

public enum Expiration {

    ONE_DAY,
    ONE_WEEK,
    ONE_MONTH,
    THREE_MONTHS,
    SIX_MONTHS,
    ONE_YEAR,
    NEVER;

    public long toMillis() {
        long time = System.currentTimeMillis();
        switch (this) {
            case NEVER:
                return -1;
            case ONE_DAY:
                time += TimeUnit.DAYS.toMillis(1);
                break;
            case ONE_WEEK:
                time += TimeUnit.DAYS.toMillis(7);
                break;
            case ONE_MONTH:
                time += TimeUnit.DAYS.toMillis(30);
                break;
            case THREE_MONTHS:
                time += TimeUnit.DAYS.toMillis(30 * 3);
                break;
            case SIX_MONTHS:
                time += TimeUnit.DAYS.toMillis(30 * 6);
                break;
            case ONE_YEAR:
                time += TimeUnit.DAYS.toMillis((long) 365.25);
                break;
        }
        return time;
    }

}
