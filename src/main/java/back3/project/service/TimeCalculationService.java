package back3.project.service;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TimeCalculationService {

    public String formatDuration(Duration duration) {
        if (duration == null) {
            return null; // Или "" или другое значение по умолчанию
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

}