package app.monthmark.core;

import org.junit.Test;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.Assert.*;

public class CountdownTest {
    private Countdown friday() { return Countdown.monthly("pay", "Days to payday", DayOfWeek.FRIDAY, 1, 0); }

    @Test public void lastFridayAndYearRollover() {
        assertEquals(LocalDate.of(2026,11,27), friday().target(LocalDate.of(2026,11,14)));
        assertEquals(LocalDate.of(2027,1,29), friday().target(LocalDate.of(2026,12,26)));
    }
    @Test public void dueTodayDoesNotJumpUntilTomorrow() {
        assertEquals(0, friday().days(LocalDate.of(2026,11,27)));
        assertEquals(LocalDate.of(2026,12,25), friday().target(LocalDate.of(2026,11,28)));
    }
    @Test public void leapFebruary() {
        Countdown c = Countdown.monthly("x", "Leap", DayOfWeek.THURSDAY, 1, 0);
        assertEquals(LocalDate.of(2024,2,29), c.target(LocalDate.of(2024,2,1)));
        assertEquals(LocalDate.of(2025,2,27), c.target(LocalDate.of(2025,2,1)));
    }
    @Test public void fixedDatesAndPastDates() {
        Countdown c = Countdown.fixed("x", "Days to holiday", LocalDate.of(2026,12,1), 0);
        assertEquals(17, c.days(LocalDate.of(2026,11,14)));
        assertEquals(0, c.days(LocalDate.of(2026,12,1)));
        assertEquals(-1, c.days(LocalDate.of(2026,12,2)));
        assertEquals(LocalDate.of(2026,12,1), c.target(LocalDate.of(2027,1,1)));
    }
    @Test public void dstUsesCalendarDays() {
        LocalDate start = ZonedDateTime.of(2026,3,28,23,30,0,0,ZoneId.of("Europe/London")).toLocalDate();
        assertEquals(2, Countdown.fixed("x", "Trip", LocalDate.of(2026,3,30), 0).days(start));
    }
    @Test public void nextOccurrencesRemainInTheirOwnMonths() {
        assertEquals(List.of(LocalDate.of(2026,11,27),LocalDate.of(2026,12,25),LocalDate.of(2027,1,29)),
                friday().upcoming(LocalDate.of(2026,11,14),3));
    }
    @Test public void everyWeekdayAndOffsetOverFull400YearCycle() {
        for (int year = 2000; year < 2400; year++) for (int m = 1; m <= 12; m++) {
            YearMonth month = YearMonth.of(year,m);
            for (DayOfWeek day : DayOfWeek.values()) for (int n = 1; n <= 4; n++) {
                Countdown c = Countdown.monthly("x", "Test", day, n, 0);
                LocalDate result = c.inMonth(month);
                assertEquals(month, YearMonth.from(result));
                assertEquals(day, result.getDayOfWeek());
                long gap = ChronoUnit.DAYS.between(result, month.atEndOfMonth());
                assertTrue(gap >= (n-1)*7L && gap < n*7L);
                assertEquals(result, c.target(result));
                assertEquals(c.inMonth(month.plusMonths(1)), c.target(result.plusDays(1)));
            }
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsEmptyName() {
        Countdown.fixed("x", "  ", LocalDate.now(), 0);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsImpossibleOffset() {
        Countdown.monthly("x", "Test", DayOfWeek.MONDAY, 5, 0);
    }
}
