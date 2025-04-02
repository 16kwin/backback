package back3.project.service;

import back3.project.dto.NormDto;
import back3.project.dto.OperationDto;
import back3.project.entity.PppNorms;
import back3.project.entity.PppOperation;
import back3.project.repository.PppEmployeesRepository;
import back3.project.repository.PppNormsRepository;
import back3.project.repository.PppOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationCalculationService {

    private final PppEmployeesRepository pppEmployeesRepository;
    private final PppNormsRepository pppNormsRepository;
    private final PppOperationRepository pppOperationRepository;
    private final TimeCalculationService timeCalculationService;
    private final PppConversionService pppConversionService;

    public OperationDto createAggregatedOperationDto(List<PppOperation> operations, String transaction) {
        System.out.println("Operations in createAggregatedOperationDto: " + operations);
        if (operations == null || operations.isEmpty()) {
            return null;
        }

        // Find the earliest start time (handling nulls)
        PppOperation earliestStart = operations.stream()
                .filter(op -> op.getStartTime() != null)
                .min(Comparator.comparing(PppOperation::getStartTime))
                .orElse(null);

        // Find the latest stop time (handling nulls)
        PppOperation latestStop = operations.stream()
                .filter(op -> op.getStopTime() != null)
                .max(Comparator.comparing(PppOperation::getStopTime))
                .orElse(null);

        // Calculate the total duration (operation execution time)
        Duration operationDurationCalc = operations.stream()
        .filter(op -> op.getStartTime() != null && op.getStopTime() != null)
        .map(op -> op.getStartTime().toLocalDate().equals(op.getStopTime().toLocalDate())
                ? WorkingHoursCalculator.calculateWorkingHoursSameDay(op.getStartTime(), op.getStopTime())
                : WorkingHoursCalculator.calculateWorkingHours(op.getStartTime(), op.getStopTime()))
        .reduce(Duration.ZERO, Duration::plus);

        // Get any operation object to retrieve general data (type, work, employee ID)
        PppOperation anyOperation = operations.get(0);

        OperationDto operationDto = new OperationDto();
        operationDto.setOperationId(anyOperation.getOperationId());
        operationDto.setOperationType(anyOperation.getOperationType());
        operationDto.setOperationWork(anyOperation.getOperationWork());
        operationDto.setEmployee(pppConversionService.convertToEmployeeDto(pppEmployeesRepository.findById(anyOperation.getEmployeesId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + anyOperation.getEmployeesId() + " in transaction: " + transaction))));
        NormDto norm = pppConversionService.convertToNormDto(pppNormsRepository.findByOperationNormName(anyOperation.getOperationType())
                .orElseThrow(() -> new EntityNotFoundException("Norm not found with name: " + anyOperation.getOperationType() + " in transaction: " + transaction)));
        operationDto.setNorm(norm);

        String operationDuration = timeCalculationService.formatDuration(operationDurationCalc);
        operationDto.setOperationDuration(operationDuration);

        // Format the total duration
        LocalDateTime startTime = null;
        LocalDateTime stopTime = null;

        if (earliestStart != null) {
            startTime = earliestStart.getStartTime();
            operationDto.setStartTime(startTime);
        }
        if (latestStop != null) {
            stopTime = latestStop.getStopTime();
            operationDto.setStopTime(stopTime);
        }

        // Get the work type for the operation
        String operationWorkType = getOperationWorkType(anyOperation.getOperationType());

        // Calculate the sum of the option norms related to this operation
        Double optionNorm = calculateOptionNorm(anyOperation.getOperationType(), operationWorkType, transaction);

        // Set optionNorm to OperationDto
        operationDto.setOptionNorm(optionNorm);

        // Find and add the option time
        Duration optionsDurationCalc = calculateOptionsDuration(anyOperation.getOperationType(), operationWorkType, transaction);
        String optionsDuration = timeCalculationService.formatDuration(optionsDurationCalc);
        operationDto.setOptionsDuration(optionsDuration);

        // Calculate totalDuration
        Duration totalDurationCalc = operationDurationCalc.plus(optionsDurationCalc);
        String totalDuration = timeCalculationService.formatDuration(totalDurationCalc);
        operationDto.setTotalDuration(totalDuration);

        // Calculate and set isTimeExceedsNorm
        double totalDurationInHours = operationDurationCalc.toMinutes() / 60.0; // Convert to hours
        boolean isTimeExceedsNorm = totalDurationInHours < (norm.getOperationNorm() == null ? 0 : Double.parseDouble(norm.getOperationNorm())) + (operationDto.getOptionNorm() == null ? 0 : operationDto.getOptionNorm());
        operationDto.setIsTimeExceedsNorm(isTimeExceedsNorm);

        return operationDto;
    }

    private String getOperationWorkType(String operationType) {
        // Create a Map to store the correspondence between operation and work type
        Map<String, String> operationWorkTypes = new HashMap<>();
        operationWorkTypes.put("Входной контроль", "Комплектация");
        operationWorkTypes.put("Выходной контроль", "Комплектация");
        operationWorkTypes.put("Подключение", "Электрик");
        operationWorkTypes.put("Проверка механиком", "Механик");
        operationWorkTypes.put("Проверка технологом", "Технолог");
        operationWorkTypes.put("Проверка электронщиком", "Электронщик");
        operationWorkTypes.put("Транспортное положение", "Электрик");

        // Check if the operation is in the Map
        if (operationWorkTypes.containsKey(operationType)) {
            // Return the work type for the given operation
            return operationWorkTypes.get(operationType);
        } else {
            // If the operation is not found, return the default value or throw an exception
            System.err.println("Warning: Work type not found for operation " + operationType);
            return ""; // Return an empty string as the default value
            // Or throw an exception:
            // throw new IllegalArgumentException("Work type not found for operation " + operationType);
        }
    }

    private Double calculateOptionNorm(String operationType, String operationWorkType, String transaction) {
        double optionNormSum = 0.0;

        // Get all options for this transaction
        List<PppOperation> options = pppOperationRepository.findByTransactionAndCategory(transaction, "Опция");

        // For each option, check if it belongs to the current operation
        for (PppOperation option : options) {
            // Get the norm for the option
            PppNorms norm = pppNormsRepository.findByOperationNormName(option.getOperationType())
                    .orElse(null); // Handle the case where the norm is not found

            // If the norm is found and the work type of the option corresponds to the work type of the operation then add the norm to the sum
            if (norm != null && operationWorkType != null && operationWorkType.equals(norm.getOperationType())) { // Corrected line
                try {
                    optionNormSum += Double.parseDouble(norm.getOperationNorm());
                } catch (NumberFormatException e) {
                    // Handle the case where operationNorm is not a number
                    System.err.println("Error: Could not convert operationNorm to a number for option " + option.getOperationType());
                }
            }
        }

        return optionNormSum;
    }

    private Duration calculateOptionsDuration(String operationType, String operationWorkType, String transaction) {
        Duration optionsDuration = Duration.ZERO;

        // Get all options for this transaction
        List<PppOperation> options = pppOperationRepository.findByTransactionAndCategory(transaction, "опция");

        // For each option, check if it belongs to the current operation
        for (PppOperation option : options) {
            // Get the norm for the option (use it to check the work type)
            PppNorms norm = pppNormsRepository.findByOperationNormName(option.getOperationType())
                    .orElse(null);

            // If the norm is found and the work type of the option corresponds to the work type of the operation then add the duration of the option to the sum
            if (norm != null && operationWorkType != null && operationWorkType.equals(norm.getOperationType())) {
                if (option.getStartTime() != null && option.getStopTime() != null) {
                    optionsDuration = optionsDuration.plus(Duration.between(option.getStartTime(), option.getStopTime()));
                }
            }
        }

        return optionsDuration;
    }
}