package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.OperationTime; // Import OperationTime
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterOperationTimeService {

    private final TimeCalculationService timeCalculationService;

    private static final Map<String, String> OPERATION_PRECEDENCE = new HashMap<>();

    static {
        OPERATION_PRECEDENCE.put("Подключение", "Входной контроль");
        OPERATION_PRECEDENCE.put("Проверка механиком", "Подключение");
        OPERATION_PRECEDENCE.put("Проверка электронщиком", "Проверка механиком");
        OPERATION_PRECEDENCE.put("Проверка технологом", "Проверка электронщиком");
        OPERATION_PRECEDENCE.put("Выходной контроль", "Проверка технологом");
        OPERATION_PRECEDENCE.put("Транспортное положение", "Выходной контроль");
    }

    public List<OperationTime> calculateTimeDifferences(List<OperationDto> operations) { // Change return type
        List<OperationTime> operationTimes = new ArrayList<>(); // Change list type

        for (OperationDto currentOperation : operations) {
            String currentOperationType = currentOperation.getOperationType();

            // Для "Входного контроля" не вычисляем разницу во времени, **ПРОПУСКАЕМ**
            if ("Входной контроль".equals(currentOperationType)) {
                continue; // Переходим к следующей итерации цикла, ничего не добавляя в timeDifferences
            }

            String previousOperationType = OPERATION_PRECEDENCE.get(currentOperationType);
            OperationDto previousOperation = findPreviousOperation(operations, previousOperationType, currentOperation);

            if (previousOperation != null && currentOperation.getStartTime() != null && previousOperation.getStopTime() != null) {
                Duration timeDifference = Duration.between(previousOperation.getStopTime(), currentOperation.getStartTime());
                String formattedDuration = timeCalculationService.formatDuration(timeDifference);

                // Create OperationTime object and add it to the list
                OperationTime operationTime = new OperationTime();
                operationTime.setOperationType(currentOperationType);
                operationTime.setTimeDifference(formattedDuration);
                operationTimes.add(operationTime);

            } else {
                // Если предыдущая операция не найдена или время равно null, **ПРОПУСКАЕМ**
                // Не добавляем null в timeDifferences
            }
        }

        return operationTimes; // Return the list of OperationTime objects
    }

    private OperationDto findPreviousOperation(List<OperationDto> operations, String previousOperationType, OperationDto currentOperation) {
        for (OperationDto operation : operations) {
            if (previousOperationType != null && previousOperationType.equals(operation.getOperationType()) && operation.getStopTime() != null) {
                return operation;
            }
        }
        return null;
    }
}