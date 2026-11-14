package app.monthmark.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Date-only countdowns. Today stays due all day; monthly rules roll over tomorrow. */
public final class Countdown {
    public final String id;
    public final String title;
    public final LocalDate fixedDate;
    public final DayOfWeek weekday;
    public final int fromEnd;
    public final int color;

    private Countdown(String id, String title, LocalDate fixedDate, DayOfWeek weekday, int fromEnd, int color) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title).trim();
        if (this.title.isEmpty()) throw new IllegalArgumentException("Give your countdown a name");
        this.fixedDate = fixedDate;
        this.weekday = weekday;
        this.fromEnd = fromEnd;
        this.color = color;
    }

    public static Countdown fixed(String id, String title, LocalDate date, int color) {
        return new Countdown(id, title, Objects.requireNonNull(date), null, 0, color);
    }

    public static Countdown monthly(String id, String title, DayOfWeek weekday, int fromEnd, int color) {
        if (fromEnd < 1 || fromEnd > 4) throw new IllegalArgumentException("Choose last through fourth-last");
        return new Countdown(id, title, null, Objects.requireNonNull(weekday), fromEnd, color);
    }

    public boolean isMonthly() { return weekday != null; }

    public LocalDate inMonth(YearMonth month) {
        if (!isMonthly()) throw new IllegalStateException("This countdown uses a fixed date");
        return month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(weekday)).minusWeeks(fromEnd - 1L);
    }

    public LocalDate target(LocalDate today) {
        if (!isMonthly()) return fixedDate;
        YearMonth month = YearMonth.from(today);
        LocalDate date = inMonth(month);
        return date.isBefore(today) ? inMonth(month.plusMonths(1)) : date;
    }

    public long days(LocalDate today) { return ChronoUnit.DAYS.between(today, target(today)); }

    public List<LocalDate> upcoming(LocalDate today, int count) {
        List<LocalDate> dates = new ArrayList<>();
        if (count <= 0) return dates;
        LocalDate next = target(today);
        dates.add(next);
        if (isMonthly()) {
            YearMonth month = YearMonth.from(next);
            for (int i = 1; i < count; i++) dates.add(inMonth(month.plusMonths(i)));
        }
        return dates;
    }
}
