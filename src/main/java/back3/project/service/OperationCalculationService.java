package back3.project.service;

import back3.project.dto.EmployeeDto;
import back3.project.dto.NormDto;
import back3.project.dto.OperationDto;
import back3.project.entity.PppEmployees;
import back3.project.entity.PppNorms;
import back3.project.entity.PppOperation;
import back3.project.entity.Problems;
import back3.project.repository.PppEmployeesRepository;
import back3.project.repository.PppNormsRepository;
import back3.project.repository.PppOperationRepository;
import back3.project.repository.ProblemsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
public class OperationCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(OperationCalculationService.class);

    private final PppEmployeesRepository pppEmployeesRepository;
    private final PppNormsRepository pppNormsRepository;
    private final PppOperationRepository pppOperationRepository;
    private final TimeCalculationService timeCalculationService;
    private final PppConversionService pppConversionService;
    private final ProblemsRepository problemsRepository;

    public OperationDto createAggregatedOperationDto(List<PppOperation> operations, String transaction) {
        logger.debug("createAggregatedOperationDto() called with operations: {} and transaction: {}", operations, transaction);
        if (operations == null || operations.isEmpty()) {
            logger.debug("Operations list is null or empty, returning null.");
            return null;
        }

        // Get any operation object to retrieve general data (type, work, employee ID)
        PppOperation anyOperation = operations.get(0);

        // Check if employeeId is null
        Long employeeId = anyOperation.getEmployeesId();
        if (employeeId == null) {
            logger.warn("Skipping operation because employeeId is null");
            return null; // Skip creating an operation if employeeId is null
        }

        //Check if employees exist
        Optional<PppEmployees> optionalPppEmployees = pppEmployeesRepository.findById(employeeId);
        if (optionalPppEmployees.isEmpty()) {
            logger.warn("Skipping operation because employeeId not found in database");
            return null; // Skip creating an operation if employeeId is not found
        }
        String operationWorkType = getOperationWorkType(anyOperation.getOperationType());

        if (operationWorkType == null || operationWorkType.isEmpty()) {
            logger.warn("Skipping operation because operationWorkType is null or empty for operation: " + anyOperation.getOperationType());
            return null; // Skip creating an operation if operationWorkType is not found
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

        // Check for incomplete operations (start time but no stop time)
        boolean hasIncompleteOperation = operations.stream()
                .anyMatch(op -> op.getStartTime() != null && op.getStopTime() == null);

        // Calculate the total duration (operation execution time) only if every norm is complete
        Duration operationDurationCalc = operations.stream()
                .filter(op -> op.getStartTime() != null && op.getStopTime() != null)
                .map(op -> op.getStartTime().toLocalDate().equals(op.getStopTime().toLocalDate())
                        ? WorkingHoursCalculator.calculateWorkingHoursSameDay(op.getStartTime(), op.getStopTime())
                        : WorkingHoursCalculator.calculateWorkingHours(op.getStartTime(), op.getStopTime()))
                .reduce(Duration.ZERO, Duration::plus);

        OperationDto operationDto = new OperationDto();
        operationDto.setOperationId(anyOperation.getOperationId());
        operationDto.setOperationType(anyOperation.getOperationType());

        EmployeeDto employeeDto = pppConversionService.convertToEmployeeDto(optionalPppEmployees.get());
        operationDto.setEmployee(employeeDto);

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

        // Calculate the sum of the option norms related to this operation
        Double optionNorm = calculateOptionNorm(anyOperation.getOperationType(), operationWorkType, transaction);
        logger.debug("Calculated optionNorm: {} for operationType: {}", optionNorm, anyOperation.getOperationType());

        // Set optionNorm to OperationDto
        operationDto.setOptionNorm(optionNorm);
        // Find and add the option time
        Duration optionsDurationCalc = calculateOptionsDuration(anyOperation.getOperationType(), operationWorkType, transaction);
        String optionsDuration = timeCalculationService.formatDuration(optionsDurationCalc);
        operationDto.setOptionsDuration(optionsDuration);

        Double problemsNormHours = calculateProblemsNormHours(transaction, anyOperation.getEmployeesId());
        operationDto.setProblemsNormHours(problemsNormHours);
        // Calculate totalDuration
        String totalDuration = null;  // start with null
        if (!hasIncompleteOperation) { //if every operation complete we do this
            Duration totalDurationCalc = operationDurationCalc.plus(optionsDurationCalc);
            totalDuration = timeCalculationService.formatDuration(totalDurationCalc);
        }

        operationDto.setTotalDuration(totalDuration); //Total duration will be null in case of open time in some norm
        logger.debug("Returning OperationDto: {}", operationDto);
        return operationDto;
    }

    private Double calculateProblemsNormHours(String transaction, Long employeeId) {
        List<Problems> problems = problemsRepository.findByTransactionAndIdEmployee(transaction, employeeId);
        if (problems == null || problems.isEmpty()) {
            return 0.0;  // Или null, в зависимости от того, какое значение по умолчанию вы хотите.
        }
        return problems.stream()
                .mapToDouble(problem -> (problem.getNormHours() != null ? problem.getNormHours() : 0.0)) // Обработка null
                .sum();
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
            logger.warn("Work type not found for operation " + operationType);
            return ""; // Return an empty string as the default value
            // Or throw an exception:
            // throw new IllegalArgumentException("Work type not found for operation " + operationType);
        }
    }
    private Double calculateOptionNorm(String operationType, String operationWorkType, String transaction) {
        logger.debug("calculateOptionNorm() called for operationType: {}, operationWorkType: {}, transaction: {}", operationType, operationWorkType, transaction);
        double optionNormSum = 0.0;
    
        // Get all options for this transaction
        List<PppOperation> options = pppOperationRepository.findByTransaction(transaction);
        logger.debug("Found {} options for transaction: {}", options.size(), transaction);
    
        // For each option, check if it belongs to the current operation
        for (PppOperation option : options) {
            // Get the norm for the option
            Optional<PppNorms> normOptional = pppNormsRepository.findByOperationNormName(option.getOperationType());
            PppNorms norm = null;
    
            if (normOptional.isPresent()) {
                norm = normOptional.get();
            }
    
            logger.debug("OPERATION: {}, CATEGORY: {}, EMPLOYEE_ID: {}", option.getOperationType(), (norm != null ? norm.getCategory() : null), option.getEmployeesId());
    
            // If the norm is found and it is an "Опция" (ignore case) and employeesId is not null and operationWorkType matches the norm's operation type
            if (norm != null && "Опция".equalsIgnoreCase(norm.getCategory()) && option.getEmployeesId() != null && operationWorkType != null && operationWorkType.equals(norm.getOperationType())) {
                try {
                    optionNormSum += Double.parseDouble(norm.getOperationNorm());
                    logger.debug("Adding option norm: {} for operation: {}", norm.getOperationNorm(), option.getOperationType());
    
                } catch (NumberFormatException e) {
                    // Handle the case where operationNorm is not a number
                    logger.error("Could not convert operationNorm to a number for option " + option.getOperationType(), e);
                }
            }
        }
        logger.debug("Returning optionNormSum: {} for operationType: {}", optionNormSum, operationType);
        return optionNormSum;
    }

    private Duration calculateOptionsDuration(String operationType, String operationWorkType, String transaction) {
        logger.debug("calculateOptionsDuration() called for operationType: {}, operationWorkType: {}, transaction: {}", operationType, operationWorkType, transaction);
        Duration optionsDuration = Duration.ZERO;
    
        // Get all options for this transaction
        List<PppOperation> options = pppOperationRepository.findByTransaction(transaction);
        logger.debug("Found {} options for transaction: {}", options.size(), transaction);
    
        // For each option, check if it belongs to the current operation
        for (PppOperation option : options) {
    
            Optional<PppNorms> normOptional = pppNormsRepository.findByOperationNormName(option.getOperationType());
            PppNorms norm = null;
    
            if (normOptional.isPresent()) {
                norm = normOptional.get();
            }
    
             logger.debug("OPERATION: {}, CATEGORY: {}, EMPLOYEE_ID: {}", option.getOperationType(), (norm != null ? norm.getCategory() : null), option.getEmployeesId());
    
            // If the norm is found and it is an "Опция" (ignore case) and employeesId is not null and operationWorkType matches the norm's operation type
            if (norm != null && "Опция".equalsIgnoreCase(norm.getCategory()) && option.getEmployeesId() != null &&  operationWorkType != null && operationWorkType.equals(norm.getOperationType())) {
                if (option.getStartTime() != null && option.getStopTime() != null) {
                    Duration duration = Duration.between(option.getStartTime(), option.getStopTime());
                    optionsDuration = optionsDuration.plus(duration);
                    logger.debug("Adding duration: {} for option: {}", duration, option.getOperationType());
                }
            }
        }
    
        logger.debug("Returning optionsDuration: {} for operationType: {}", optionsDuration, operationType);
        return optionsDuration;
    }
}