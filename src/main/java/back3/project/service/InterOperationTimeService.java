package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.OperationTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class InterOperationTimeService {

    private final TimeCalculationService timeCalculationService;
    private static final Logger logger = LoggerFactory.getLogger(InterOperationTimeService.class);

    private static final Map<String, String> OPERATION_PRECEDENCE = new HashMap<>();

    static {
        OPERATION_PRECEDENCE.put("Подключение", "Входной контроль");
        OPERATION_PRECEDENCE.put("Проверка механиком", "Подключение");
        OPERATION_PRECEDENCE.put("Проверка электронщиком", "Проверка механиком");
        OPERATION_PRECEDENCE.put("Проверка технологом", "Проверка электронщиком");
        OPERATION_PRECEDENCE.put("Выходной контроль", "Проверка технологом");
        OPERATION_PRECEDENCE.put("Транспортное положение", "Выходной контроль");
    }

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 30);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 30);

    public List<OperationTime> calculateTimeDifferences(List<OperationDto> operations) {
        List<OperationTime> operationTimes = new ArrayList<>();

        for (OperationDto currentOperation : operations) {
            if (currentOperation == null) {
                logger.warn("currentOperation is null, skipping this iteration");
                continue;
            }
            String currentOperationType = currentOperation.getOperationType();

            if ("Входной контроль".equals(currentOperationType)) {
                continue;
            }

            String previousOperationType = OPERATION_PRECEDENCE.get(currentOperationType);
            OperationDto previousOperation = findPreviousOperation(operations, previousOperationType, currentOperation);

            if (previousOperation != null && currentOperation.getStartTime() != null && previousOperation.getStopTime() != null) {
                LocalDateTime previousStopTime = previousOperation.getStopTime();
                LocalDateTime currentStartTime = currentOperation.getStartTime();

                Duration timeDifference;
                if (currentStartTime.isBefore(previousStopTime)) {
                    // Если currentStartTime раньше previousStopTime, просто вычисляем разницу (отрицательное значение)
                    timeDifference = Duration.between(previousStopTime, currentStartTime);
                } else {
                    // Иначе учитываем рабочие часы и выходные
                    timeDifference = calculateWorkingHours(previousStopTime, currentStartTime);
                }

                String formattedDuration = timeCalculationService.formatDuration(timeDifference);

                OperationTime operationTime = new OperationTime();
                operationTime.setOperationType(currentOperationType);
                operationTime.setTimeDifference(formattedDuration);
                operationTimes.add(operationTime);
            }
        }

        return operationTimes;
    }

    private Duration calculateWorkingHours(LocalDateTime start, LocalDateTime end) {
        Duration totalWorkingTime = Duration.ZERO;
        LocalDateTime current = start;
        boolean negative = false;

        if (end.isBefore(start)) {
            LocalDateTime temp = start;
            start = end;
            end = temp;
            negative = true;
        }

        while (current.isBefore(end)) {
            if (!isWeekend(current.toLocalDate())) {
                LocalDateTime workdayStart = current.toLocalDate().atTime(WORKDAY_START);
                LocalDateTime workdayEnd = current.toLocalDate().atTime(WORKDAY_END);

                LocalDateTime intervalStart = current.isBefore(workdayStart) ? workdayStart : current;
                LocalDateTime intervalEnd = end.isBefore(workdayEnd) ? end : workdayEnd;

                if (intervalStart.isBefore(intervalEnd)) {
                    totalWorkingTime = totalWorkingTime.plus(Duration.between(intervalStart, intervalEnd));
                }
            }
            current = current.plusDays(1).with(WORKDAY_START);
        }
        if(negative){
            totalWorkingTime = totalWorkingTime.negated();
        }
        return totalWorkingTime;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private OperationDto findPreviousOperation(List<OperationDto> operations, String previousOperationType, OperationDto currentOperation) {
        for (OperationDto operation : operations) {
            if (operation != null) {
                String operationType = operation.getOperationType();
                if (operationType != null && previousOperationType != null && previousOperationType.equals(operationType) && operation.getStopTime() != null) {
                    return operation;
                }
            } else {
                System.err.println("Warning: Null operation found in list.");
            }
        }
        return null;
    }
}