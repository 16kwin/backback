package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.NormDto;
import back3.project.entity.PppOperation;
import back3.project.entity.PppNorms;
import back3.project.repository.PppOperationRepository;
import back3.project.repository.PppNormsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationService {

    private static final Logger logger = LoggerFactory.getLogger(OperationService.class);

    private final PppOperationRepository pppOperationRepository;
    private final OperationCalculationService operationCalculationService;
    private final PppNormsRepository pppNormsRepository;

    public boolean isEmployeeBusyNow(Long employeesId, LocalDateTime now) {
        logger.info("isEmployeeBusyNow() called for employee: " + employeesId + " at time: " + now);
        List<PppOperation> operations = pppOperationRepository.findByEmployeesIdAndStartTimeBeforeAndStopTimeAfter(employeesId, now, now);
        return !operations.isEmpty();
    }

    private static final Map<String, Integer> OPERATION_ORDER = new HashMap<>();
    public static final List<String> ALLOWED_OPERATION_TYPES = Arrays.asList(
            "Входной контроль",
            "Подключение",
            "Проверка механиком",
            "Проверка электронщиком",
            "Проверка технологом",
            "Выходной контроль",
            "Транспортное положение"
    );

    static {
        OPERATION_ORDER.put("Входной контроль", 1);
        OPERATION_ORDER.put("Подключение", 2);
        OPERATION_ORDER.put("Проверка механиком", 3);
        OPERATION_ORDER.put("Проверка электронщиком", 4);
        OPERATION_ORDER.put("Проверка технологом", 5);
        OPERATION_ORDER.put("Выходной контроль", 6);
        OPERATION_ORDER.put("Транспортное положение", 7);
    }

    public List<OperationDto> getAggregatedOperations(String transaction) {
        logger.info("getAggregatedOperations() called for transaction: {}", transaction);

        List<OperationDto> operationDtos = new ArrayList<>();

        for (String operationType : ALLOWED_OPERATION_TYPES) {
            logger.debug("Processing operation type: {} for transaction: {}", operationType, transaction);

            // Get operations from the database for the current operation type
            List<PppOperation> operations = null;
            try {
                operations = pppOperationRepository.findByTransactionAndEmployeesIdIsNotNullAndOperationType(transaction, operationType);
                logger.debug("Found {} operations of type {} for transaction: {}", operations.size(), operationType, transaction);
            } catch (Exception e) {
                logger.error("Error while fetching operations of type {} for transaction: {}", operationType, transaction, e);
                operations = Collections.emptyList(); // Treat exception as no operations found
            }

            // If no operations found, create a default OperationDto
            if (operations == null || operations.isEmpty()) {
                logger.info("No operations found for type {} and transaction: {}. Creating default operation DTO.", operationType, transaction);
                OperationDto defaultOperationDto = createDefaultOperationDto(transaction, operationType);
                if (defaultOperationDto != null) {
                    operationDtos.add(defaultOperationDto);
                }
            } else {
                logger.debug("Aggregating operations of type {} for transaction: {}", operationType, transaction);
                // Aggregate the operations using OperationCalculationService
                OperationDto aggregatedOperationDto = operationCalculationService.createAggregatedOperationDto(operations, transaction);
                if (aggregatedOperationDto != null) {
                    aggregatedOperationDto.setIsTimeExceedsNorm(isTimeExceedsNorm(aggregatedOperationDto));
                    operationDtos.add(aggregatedOperationDto);
                }
            }
        }

        logger.info("Returning {} aggregated operations for transaction: {}", operationDtos.size(), transaction);
        return operationDtos;
    }


    private OperationDto createDefaultOperationDto(String transaction, String operationType) {
        // Get norm from PppNorms table by operationType
        PppNorms norm = pppNormsRepository.findByOperationNormName(operationType)
                .orElse(null); // Handle the case when norm is not found

        if (norm != null) {
            NormDto normDto = new NormDto();
            normDto.setOperationNormName(norm.getOperationNormName());
            normDto.setOperationNorm(norm.getOperationNorm());
            normDto.setOperationType(norm.getOperationType());

            OperationDto defaultOperationDto = new OperationDto();
            defaultOperationDto.setOperationType(operationType);
            defaultOperationDto.setNorm(normDto); // Set the norm DTO

            // Set default values for other fields
            defaultOperationDto.setOperationId(null);
            defaultOperationDto.setOperationWork(null);
            defaultOperationDto.setStartTime(null);
            defaultOperationDto.setStopTime(null);
            defaultOperationDto.setEmployee(null);
            defaultOperationDto.setOperationDuration("00:00:00");
            defaultOperationDto.setOptionsDuration("00:00:00");
            defaultOperationDto.setTotalDuration("00:00:00");
            defaultOperationDto.setOptionNorm(0.0);
            defaultOperationDto.setIsTimeExceedsNorm(false);
            defaultOperationDto.setCategory(null);
            defaultOperationDto.setProblemsNormHours(0.0);

            logger.info("Created default OperationDto for operation type: {} and transaction: {}", operationType, transaction);
            return defaultOperationDto;
        } else {
            logger.warn("Norm not found for operation type: {}", operationType);
            return null;
        }
    }


    public List<OperationDto> getAllAggregatedOperations() {
        logger.info("getAllAggregatedOperations() called");

        List<PppOperation> operations = null;
        try {
            // Get all operations from the database, filtering out those with null employeesId
            operations = pppOperationRepository.findByEmployeesIdIsNotNull();
            logger.debug("Found {} main operations", operations.size());
        } catch (Exception e) {
            logger.error("Error while fetching operations", e);
            return List.of();
        }

        // Filter operations to only include allowed types
        operations = operations.stream()
                .filter(op -> ALLOWED_OPERATION_TYPES.contains(op.getOperationType()))
                .collect(Collectors.toList());

        logger.debug("After filtering, {} operations remain", operations.size());

        // Sort operations by predefined order
        operations.sort(Comparator.comparing(op -> OPERATION_ORDER.getOrDefault(op.getOperationType(), Integer.MAX_VALUE)));
        logger.debug("Operations sorted by OPERATION_ORDER");

        // Group operations by transaction
        Map<String, List<PppOperation>> operationsByTransaction = operations.stream()
                .collect(Collectors.groupingBy(PppOperation::getTransaction));
        logger.debug("Operations grouped by transaction. Found {} transactions", operationsByTransaction.size());

        // Create aggregated operations for each transaction
        List<OperationDto> operationDtos = operationsByTransaction.entrySet().stream()
                .flatMap(entry -> {
                    String transaction = entry.getKey();
                    List<PppOperation> transactionOperations = entry.getValue();
                    logger.debug("Creating aggregated operation DTOs for transaction: {}", transaction);

                    // Group operations by type before creating DTOs
                    Map<String, List<PppOperation>> operationsByType = transactionOperations.stream()
                            .collect(Collectors.groupingBy(PppOperation::getOperationType));

                    return operationsByType.entrySet().stream()
                            .map(typeEntry -> {
                                List<PppOperation> operationsOfType = typeEntry.getValue();
                                OperationDto operationDto = operationCalculationService.createAggregatedOperationDto(operationsOfType, transaction);
                                if (operationDto != null) {
                                    operationDto.setIsTimeExceedsNorm(isTimeExceedsNorm(operationDto));
                                }
                                return operationDto;
                            });
                })
                .collect(Collectors.toList());

        logger.info("Returning {} aggregated operations for all transactions", operationDtos.size());
        return operationDtos;
    }

    public boolean isTimeExceedsNorm(OperationDto operationDto) {
        // Проверяем, что operationDto не null
        if (operationDto == null) {
            logger.warn("OperationDto is null, cannot determine if time exceeds norm");
            return false;
        }

        // Получаем totalDuration в секундах
        long totalDurationSeconds = parseDurationToSeconds(operationDto.getTotalDuration());

        // Считаем сумму operationNorm и optionNorm
        NormDto norm = operationDto.getNorm();
        double totalNormHours = Double.parseDouble(norm.getOperationNorm()) + operationDto.getOptionNorm();

        // Переводим totalNormHours в секунды
        long totalNormSeconds = (long) (totalNormHours * 3600);

        return totalDurationSeconds < totalNormSeconds; // Изменено на ">"
    }

    private long parseDurationToSeconds(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }
        try {
            Duration parsedDuration = Duration.parse(duration);
            return parsedDuration.getSeconds();
        } catch (DateTimeParseException e) {
            logger.warn("Invalid duration format: {}.  Attempting to parse as HH:mm:ss", duration, e);
            // Попытка разобрать как HH:mm:ss
            String[] parts = duration.split(":");
            if (parts.length != 3) {
                logger.error("Invalid duration format: {}", duration);
                return 0;
            }
            try {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                long seconds = Long.parseLong(parts[2]);
                return hours * 3600 + minutes * 60 + seconds;
            } catch (NumberFormatException ex) {
                logger.error("Error parsing duration: {}", duration, ex);
                return 0;
            }
        }
    }
}