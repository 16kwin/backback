package back3.project.service;

import back3.project.dto.*;
import back3.project.entity.Ppp;
import back3.project.entity.PppEmployees;
import back3.project.entity.PppNorms;
import back3.project.entity.PppOperation;
import back3.project.repository.PppEmployeesRepository;
import back3.project.repository.PppNormsRepository;
import back3.project.repository.PppOperationRepository;
import back3.project.repository.PppRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PppService {

    private final PppRepository pppRepository;
    private final PppOperationRepository pppOperationRepository;
    private final PppEmployeesRepository pppEmployeesRepository;
    private final PppNormsRepository pppNormsRepository;

    public PppListDto getAllPpps() {
        List<Ppp> ppps = pppRepository.findAll();

        List<PppDto> pppDtos = ppps.stream()
                .map(this::convertToPppDto)
                .collect(Collectors.toList());

        PppListDto pppListDto = new PppListDto();
        pppListDto.setPpps(pppDtos);

        return pppListDto;
    }

    private PppDto convertToPppDto(Ppp ppp) {
        List<PppOperation> operations = pppOperationRepository.findByTransaction(ppp.getTransaction());

        // Фильтруем операции, оставляя только основные операции
        List<PppOperation> mainOperations = operations.stream()
                .filter(operation -> isMainOperation(operation.getOperationType()))
                .collect(Collectors.toList());

        // Группируем основные операции по типу
        Map<String, List<PppOperation>> operationsByType = mainOperations.stream()
                .collect(Collectors.groupingBy(PppOperation::getOperationType));

        // Создаем "смешанные" операции
        List<OperationDto> operationDtos = operationsByType.entrySet().stream()
                .map(entry -> createAggregatedOperationDto(entry.getValue(), ppp.getTransaction()))
                .collect(Collectors.toList());

        PppDto pppDto = new PppDto();
        pppDto.setTransaction(ppp.getTransaction());
        pppDto.setStatus(ppp.getStatus());
        pppDto.setPlanPpp(ppp.getPlanPpp() * 8);
        pppDto.setPlanDateStart(ppp.getPlanDateStart());
        pppDto.setForecastDateStart(ppp.getForecastDateStart());
        pppDto.setFactDateStart(ppp.getFactDateStart());
        pppDto.setPlanDateStop(ppp.getPlanDateStop());
        pppDto.setForecastDateStop(ppp.getForecastDateStop());
        pppDto.setFactDateStop(ppp.getForecastDateStop());
        pppDto.setPlanDateShipment(ppp.getPlanDateShipment());
        pppDto.setForecastDateShipment(ppp.getForecastDateShipment());
        pppDto.setFactDateShipment(ppp.getFactDateShipment());
        pppDto.setOperations(operationDtos);

        List<String> timeDifferences = new ArrayList<>(); // Изменено: List<String>
        for (int i = 1; i < operationDtos.size(); i++) {
            OperationDto currentOperation = operationDtos.get(i);
            OperationDto previousOperation = operationDtos.get(i - 1);
           System.out.println("Current operation: " + currentOperation.getOperationType() + ", Previous operation: " + previousOperation.getOperationType());
            if (currentOperation.getStartTime() != null && previousOperation.getStopTime() != null) {
                System.out.println("Previous stopTime: " + previousOperation.getStopTime());
                System.out.println("Current startTime: " + currentOperation.getStartTime());
                Duration timeDifference = Duration.between(previousOperation.getStopTime(), currentOperation.getStartTime());
                String formattedDuration = formatDuration(timeDifference); // Форматируем в строку
                timeDifferences.add(formattedDuration); // Добавляем строку в список
            } else {
                timeDifferences.add(null); // Или "" или другое значение по умолчанию
            }
        }
        pppDto.setTimeDifferences(timeDifferences);

        return pppDto;
    }


    private OperationDto createAggregatedOperationDto(List<PppOperation> operations, String transaction) {
         System.out.println("Operations in createAggregatedOperationDto: " + operations);
        if (operations == null || operations.isEmpty()) {
            return null;
        }
           // Находим самое раннее время начала (обрабатываем null)
    PppOperation earliestStart = operations.stream()
    .filter(op -> op.getStartTime() != null) // Фильтруем null значения
    .min(Comparator.comparing(PppOperation::getStartTime))
    .orElse(null);

// Находим самое позднее время окончания (обрабатываем null)
PppOperation latestStop = operations.stream()
    .filter(op -> op.getStopTime() != null) // Фильтруем null значения
    .max(Comparator.comparing(PppOperation::getStopTime))
    .orElse(null);

        // Вычисляем общую длительность (время выполнения операций)
        Duration operationDurationCalc = operations.stream()
                .filter(op -> op.getStartTime() != null && op.getStopTime() != null)
                .map(op -> {
                    Duration duration = Duration.between(op.getStartTime(), op.getStopTime());
                    long totalHours = duration.toHours();

                    if (totalHours > 24) {
                        long fullDays = totalHours / 24;
                        long remainingHours = totalHours % 24;
                        long workingHours = fullDays * 8 + remainingHours;
                        return Duration.ofHours(workingHours);
                    } else {
                        return duration;
                    }
                })
                .reduce(Duration.ZERO, Duration::plus);

        // Получаем любой объект операции, чтобы достать оттуда общие данные (тип, ворк, айди сотрудника)
        PppOperation anyOperation = operations.get(0);

        // Создаем DTO
        OperationDto operationDto = new OperationDto();
        operationDto.setOperationId(anyOperation.getOperationId());
        operationDto.setOperationType(anyOperation.getOperationType());
        operationDto.setOperationWork(anyOperation.getOperationWork());
        operationDto.setEmployee(convertToEmployeeDto(pppEmployeesRepository.findById(anyOperation.getEmployeesId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + anyOperation.getEmployeesId() + " in transaction: " + transaction))));
       NormDto norm = convertToNormDto(pppNormsRepository.findByOperationNormName(anyOperation.getOperationType())
                .orElseThrow(() -> new EntityNotFoundException("Norm not found with name: " + anyOperation.getOperationType() + " in transaction: " + transaction)));
        operationDto.setNorm(norm);


        String operationDuration = formatDuration(operationDurationCalc);
        operationDto.setOperationDuration(operationDuration);

        // Форматируем общую длительность
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
    
        // Получаем тип работы для операции
        String operationWorkType = getOperationWorkType(anyOperation.getOperationType());

        // Вычисляем сумму нормативов опций, относящихся к этой операции
         Double optionNorm = calculateOptionNorm(anyOperation.getOperationType(), operationWorkType, transaction);

        // Устанавливаем optionNorm в OperationDto
        operationDto.setOptionNorm(optionNorm);
        
        // Находим и добавляем время опций
        Duration optionsDurationCalc = calculateOptionsDuration(anyOperation.getOperationType(), operationWorkType, transaction);
        String optionsDuration = formatDuration(optionsDurationCalc);
        operationDto.setOptionsDuration(optionsDuration);

        //Вычисляем totalDuration
        Duration totalDurationCalc = operationDurationCalc.plus(optionsDurationCalc);
        String totalDuration = formatDuration(totalDurationCalc);
        operationDto.setTotalDuration(totalDuration);

          // Вычисляем и устанавливаем isTimeExceedsNorm
        double totalDurationInHours = operationDurationCalc.toMinutes() / 60.0; // Преобразуем в часы
        boolean isTimeExceedsNorm = totalDurationInHours > (norm.getOperationNorm() == null ? 0 :Double.parseDouble(norm.getOperationNorm())) + (operationDto.getOptionNorm() == null ? 0:operationDto.getOptionNorm());
        operationDto.setIsTimeExceedsNorm(isTimeExceedsNorm);

        return operationDto;
    }


    private String getOperationWorkType(String operationType) {
        // Создаем Map для хранения соответствия между операцией и типом работы
        Map<String, String> operationWorkTypes = new HashMap<>();
        operationWorkTypes.put("Входной контроль", "Комплектация");
        operationWorkTypes.put("Выходной контроль", "Комплектация");
        operationWorkTypes.put("Подключение", "Электрик");
        operationWorkTypes.put("Проверка механиком", "Механик");
        operationWorkTypes.put("Проверка технологом", "Технолог");
        operationWorkTypes.put("Проверка электронщиком", "Электронщик");
        operationWorkTypes.put("Транспортное положение", "Электрик");

        // Проверяем, есть ли операция в Map
        if (operationWorkTypes.containsKey(operationType)) {
            // Возвращаем тип работы для заданной операции
            return operationWorkTypes.get(operationType);
        } else {
            // Если операция не найдена, возвращаем значение по умолчанию или выбрасываем исключение
            System.err.println("Предупреждение: Тип работы не найден для операции " + operationType);
            return ""; // Возвращаем пустую строку в качестве значения по умолчанию
            // Или выбрасываем исключение:
            // throw new IllegalArgumentException("Тип работы не найден для операции " + operationType);
        }
    }

    private Double calculateOptionNorm(String operationType, String operationWorkType, String transaction) {
        double optionNormSum = 0.0;

        // Получаем все операции для данной транзакции
        List<PppOperation> allOperations = pppOperationRepository.findByTransaction(transaction);

        // Фильтруем операции, оставляя только те, которые не являются основными операциями
        List<PppOperation> options = allOperations.stream()
                .filter(operation -> !isMainOperation(operation.getOperationType()))
                .collect(Collectors.toList());

        // Для каждой опции проверяем, относится ли она к текущей операции
        for (PppOperation option : options) {
            // Получаем норматив для опции
            PppNorms norm = pppNormsRepository.findByOperationNormName(option.getOperationType())
                    .orElse(null); // Обрабатываем случай, когда норматив не найден

            // Если норматив найден и тип работы опции соответствует типу работы операции, то добавляем норматив к сумме
            if (norm != null && operationWorkType != null && operationWorkType.equals(norm.getOperationType())) { // Corrected line
                try {
                    optionNormSum += Double.parseDouble(norm.getOperationNorm());
                } catch (NumberFormatException e) {
                    // Обрабатываем случай, когда operationNorm не является числом
                    System.err.println("Ошибка: Не удалось преобразовать operationNorm в число для опции " + option.getOperationType());
                }
            }
        }

        return optionNormSum;
    }

    private Duration calculateOptionsDuration(String operationType, String operationWorkType, String transaction) {
        Duration optionsDuration = Duration.ZERO;

        // Получаем все операции для данной транзакции
        List<PppOperation> allOperations = pppOperationRepository.findByTransaction(transaction);

        // Фильтруем операции, оставляя только те, которые не являются основными операциями
        List<PppOperation> options = allOperations.stream()
                .filter(operation -> !isMainOperation(operation.getOperationType()))
                .collect(Collectors.toList());

        // Для каждой опции проверяем, относится ли она к текущей операции
        for (PppOperation option : options) {
            // Получаем норматив для опции (используем его для проверки типа работы)
            PppNorms norm = pppNormsRepository.findByOperationNormName(option.getOperationType())
                    .orElse(null);

            // Если норматив найден и тип работы опции соответствует типу работы операции, то добавляем длительность опции к сумме
            if (norm != null && operationWorkType != null && operationWorkType.equals(norm.getOperationType())) {
                if (option.getStartTime() != null && option.getStopTime() != null) {
                    optionsDuration = optionsDuration.plus(Duration.between(option.getStartTime(), option.getStopTime()));
                }
            }
        }

        return optionsDuration;
    }


    private String formatDuration(Duration duration) {
        if (duration == null) {
            return null; // Или "" или другое значение по умолчанию
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
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

    private EmployeeDto convertToEmployeeDto(PppEmployees employee) {
        EmployeeDto employeeDto = new EmployeeDto();
        employeeDto.setEmployeesId(employee.getEmployeesId());
        employeeDto.setEmployeesName(employee.getEmployeesName());
        employeeDto.setEmployeesSpecialization(employee.getEmployeesSpecialization());
        return employeeDto;
    }

    private NormDto convertToNormDto(PppNorms norm) {
        NormDto normDto = new NormDto();
        normDto.setOperationNormName(norm.getOperationNormName());
        normDto.setOperationNorm(norm.getOperationNorm());

        return normDto;
    }
}