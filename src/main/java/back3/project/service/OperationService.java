package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.entity.PppOperation;
import back3.project.repository.PppOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationService {

    private final PppOperationRepository pppOperationRepository;
    private final PppConversionService pppConversionService;  // Use conversion service

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
           List<PppOperation> operations = pppOperationRepository.findByTransaction(transaction);

        // Сортируем операции по предопределенному порядку типов операций
        operations.sort(Comparator.comparing(op -> OPERATION_ORDER.getOrDefault(op.getOperationType(), Integer.MAX_VALUE)));

        // Фильтруем операции, оставляя только основные операции
        List<PppOperation> mainOperations = operations.stream()
                .filter(operation -> isMainOperation(operation.getOperationType()))
                .collect(Collectors.toList());

        // Группируем основные операции по типу
        Map<String, List<PppOperation>> operationsByType = mainOperations.stream()
                .collect(Collectors.groupingBy(PppOperation::getOperationType));

        // Создаем "смешанные" операции
        return operationsByType.entrySet().stream()
                .map(entry -> operationCalculationService.createAggregatedOperationDto(entry.getValue(), transaction))
                .collect(Collectors.toList());
    }

    private boolean isMainOperation(String operationType) {
        List<String> mainOperationTypes = Arrays.asList(
                "Входной контроль",
                "Выходной контроль",
                "Подключение",
                "Проверка механиком",
                "Проверка технологом",
                "Проверка электронщиком",
                "Транспортное положение"
        );
        return mainOperationTypes.contains(operationType);
    }


}