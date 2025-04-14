package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.entity.PppOperation;
import back3.project.repository.PppNormsRepository;
import back3.project.repository.PppOperationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationService {

    private static final Logger logger = LoggerFactory.getLogger(OperationService.class);

    private final PppOperationRepository pppOperationRepository;
    private final PppNormsRepository pppNormsRepository;
    private final OperationCalculationService operationCalculationService;
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

        List<PppOperation> operations = null;
        try {
            // Get main operations from the database, filtering out those with null employeesId
            operations = pppOperationRepository.findByTransactionAndEmployeesIdIsNotNull(transaction);
            logger.debug("Found {} main operations for transaction: {}", operations.size(), transaction);
        } catch (Exception e) {
            logger.error("Error while fetching operations for transaction: {}", transaction, e);
            //Можно решить, что делать дальше: пробросить исключение, вернуть пустой список и т.д.
            return List.of(); //Вернем пустой список, чтобы не сломать всю цепочку
        }

        // Filter operations to only include allowed types
        operations = operations.stream()
                .filter(op -> ALLOWED_OPERATION_TYPES.contains(op.getOperationType()))
                .collect(Collectors.toList());

        logger.debug("After filtering, {} operations remain for transaction: {}", operations.size(), transaction);

        // Sort operations by predefined order
        operations.sort(Comparator.comparing(op -> OPERATION_ORDER.getOrDefault(op.getOperationType(), Integer.MAX_VALUE)));
        logger.debug("Operations sorted by OPERATION_ORDER");

        // Group operations by type
        Map<String, List<PppOperation>> operationsByType = operations.stream()
                .collect(Collectors.groupingBy(PppOperation::getOperationType));
        logger.debug("Operations grouped by type. Found {} operation types", operationsByType.size());

        // Create aggregated operations
        List<OperationDto> operationDtos = operationsByType.entrySet().stream()
                .map(entry -> {
                    logger.debug("Creating aggregated operation DTO for operation type: {}", entry.getKey());
                    OperationDto operationDto = operationCalculationService.createAggregatedOperationDto(entry.getValue(), transaction);
                    if (operationDto != null) { // Add this check
                        operationDto.setIsTimeExceedsNorm(isTimeExceedsNorm(operationDto));
                    }
                    return operationDto;
                })
                .collect(Collectors.toList());
        logger.info("Returning {} aggregated operations for transaction: {}", operationDtos.size(), transaction);
        return operationDtos;
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
        double totalNormHours = Double.parseDouble(operationDto.getNorm().getOperationNorm()) + operationDto.getOptionNorm();

        // Переводим totalNormHours в секунды
        long totalNormSeconds = (long) (totalNormHours * 3600);

        return totalDurationSeconds < totalNormSeconds; // Изменено на ">"
    }
    private long parseDurationToSeconds(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }
        String[] parts = duration.split(":");
        if (parts.length != 3) {
            logger.warn("Invalid duration format: {}", duration);
            return 0;
        }
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            logger.error("Error parsing duration: {}", duration, e);
            return 0;
        }
    }
}