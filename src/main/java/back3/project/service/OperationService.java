package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.entity.PppOperation;
import back3.project.repository.PppOperationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationService {

    private static final Logger logger = LoggerFactory.getLogger(OperationService.class);

    private final PppOperationRepository pppOperationRepository;

    private final OperationCalculationService operationCalculationService;

    private static final Map<String, Integer> OPERATION_ORDER = new HashMap<>();

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
            // Get main operations from the database
           operations = pppOperationRepository.findByTransactionAndCategory(transaction, "Операция");
            logger.debug("Found {} main operations for transaction: {}", operations.size(), transaction);
        } catch (Exception e) {
           logger.error("Error while fetching operations for transaction: {}", transaction, e);
           //Можно решить, что делать дальше: пробросить исключение, вернуть пустой список и т.д.
           return List.of(); //Вернем пустой список, чтобы не сломать всю цепочку
        }


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
                    return operationCalculationService.createAggregatedOperationDto(entry.getValue(), transaction);
                })
                .collect(Collectors.toList());
        logger.info("Returning {} aggregated operations for transaction: {}", operationDtos.size(), transaction);

        return operationDtos;
    }
}