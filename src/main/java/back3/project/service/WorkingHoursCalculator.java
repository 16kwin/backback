package back3.project.service;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class WorkingHoursCalculator {

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 30);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 30);

    public static Duration calculateWorkingHours(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return Duration.ZERO;
        }

        if (start.isAfter(end)) {
            return Duration.ZERO;
        }

        Duration totalWorkingHours = Duration.ZERO;
        LocalDateTime current = start;

        while (current.isBefore(end)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                // Пропускаем выходные дни
                current = current.plusDays(1).toLocalDate().atStartOfDay();
                continue;
            }

            if (current.toLocalDate().equals(start.toLocalDate())) {
                // Первый день
                totalWorkingHours = totalWorkingHours.plus(calculateWorkingHoursFirstDay(start, end));
                current = current.plusDays(1).toLocalDate().atStartOfDay();
            } else if (current.toLocalDate().equals(end.toLocalDate())) {
                // Последний день
                totalWorkingHours = totalWorkingHours.plus(calculateWorkingHoursLastDay(end));
                current = end;
            }
            else {
                // Проверяем, является ли "current" датой, отличной от start и end, и не выпадает ли она на выходной
                 dayOfWeek = current.getDayOfWeek();
                if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                    totalWorkingHours = totalWorkingHours.plus(Duration.ofHours(8));
                }
                current = current.plusDays(1).toLocalDate().atStartOfDay();
            }



        }

        return totalWorkingHours;
    }

    static Duration calculateWorkingHoursSameDay(LocalDateTime start, LocalDateTime end) {
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();

        if (startTime.isAfter(WORKDAY_END)) {
            return Duration.ZERO; // Операция началась после окончания рабочего дня
        }

        if (endTime.isBefore(WORKDAY_START)) {
            return Duration.ZERO; // Операция закончилась до начала рабочего дня
        }

        startTime = startTime.isBefore(WORKDAY_START) ? WORKDAY_START : startTime;
        endTime = endTime.isAfter(WORKDAY_END) ? WORKDAY_END : endTime;

        return Duration.between(startTime, endTime);
    }


    private static Duration calculateWorkingHoursFirstDay(LocalDateTime start, LocalDateTime end) {
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return calculateWorkingHoursSameDay(start, end);
        }
        LocalTime startTime = start.toLocalTime();

        if (startTime.isAfter(WORKDAY_END)) {
            return Duration.ZERO;
        }

        LocalTime endOfDay = WORKDAY_END;
        return Duration.between(startTime, endOfDay);
    }


    private static Duration calculateWorkingHoursLastDay(LocalDateTime end) {
        LocalTime endTime = end.toLocalTime();

        if (endTime.isBefore(WORKDAY_START)) {
            return Duration.ZERO;
        }

        LocalTime startOfDay = WORKDAY_START;
        return Duration.between(startOfDay, endTime);
    }


}