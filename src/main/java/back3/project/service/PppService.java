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
import java.util.Comparator;
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

        // Группируем операции по типу
        Map<String, List<PppOperation>> operationsByType = operations.stream()
                .collect(Collectors.groupingBy(PppOperation::getOperationType));

        // Создаем "смешанные" операции
        List<OperationDto> operationDtos = operationsByType.entrySet().stream()
                .map(entry -> createAggregatedOperationDto(entry.getValue(), ppp.getTransaction()))
                .collect(Collectors.toList());

        PppDto pppDto = new PppDto();
        pppDto.setTransaction(ppp.getTransaction());
        pppDto.setStatus(ppp.getStatus());
        pppDto.setPlanPpp(ppp.getPlanPpp()*8);
        pppDto.setPlanDateStart(ppp.getPlanDateStart());
        pppDto.setForecastDateStart(ppp.getForecastDateStart());
        pppDto.setFactDateStart(ppp.getFactDateStart());
        pppDto.setPlanDateStop(ppp.getPlanDateStop());
        pppDto.setForecastDateStop(ppp.getForecastDateStop());
        pppDto.setFactDateStop(ppp.getFactDateStop());
        pppDto.setPlanDateShipment(ppp.getPlanDateShipment());
        pppDto.setForecastDateShipment(ppp.getForecastDateShipment());
        pppDto.setFactDateShipment(ppp.getFactDateShipment());
        pppDto.setOperations(operationDtos);

        return pppDto;
    }

   /* private OperationDto createAggregatedOperationDto(List<PppOperation> operations, String transaction) {
        if (operations == null || operations.isEmpty()) {
            return null; // Или выбросить исключение, если это недопустимо
        }

        // Находим самое раннее время начала
        PppOperation earliestStart = operations.stream()
        .filter(op -> op.getStartTime() != null) // Фильтруем null значения
        .min(Comparator.comparing(PppOperation::getStartTime))
        .orElse(null);

// Находим самое позднее время окончания (обрабатываем null)
PppOperation latestStop = operations.stream()
        .filter(op -> op.getStopTime() != null) // Фильтруем null значения
        .max(Comparator.comparing(PppOperation::getStopTime))
        .orElse(null);

        // Получаем любой объект операции, чтобы достать оттуда общие данные (тип, ворк, айди сотрудника)
        PppOperation anyOperation = operations.get(0);

        // Создаем DTO
        OperationDto operationDto = new OperationDto();
        operationDto.setOperationId(anyOperation.getOperationId());
        operationDto.setOperationType(anyOperation.getOperationType());
        operationDto.setOperationWork(anyOperation.getOperationWork());
        operationDto.setEmployee(convertToEmployeeDto(pppEmployeesRepository.findById(anyOperation.getEmployeesId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + anyOperation.getEmployeesId() + " in transaction: " + transaction))));
        operationDto.setNorm(convertToNormDto(pppNormsRepository.findByOperationNormName(anyOperation.getOperationType())
                .orElseThrow(() -> new EntityNotFoundException("Norm not found with name: " + anyOperation.getOperationType() + " in transaction: " + transaction))));
       LocalDateTime startTime = null;
        LocalDateTime stopTime = null;
        // Устанавливаем агрегированные значения
        if (earliestStart != null) {
            startTime = earliestStart.getStartTime();
            operationDto.setStartTime(startTime);
        }
        if (latestStop != null) {
            stopTime = latestStop.getStopTime();
            operationDto.setStopTime(stopTime);
        }
        if (startTime != null && stopTime != null) {
            Duration duration = Duration.between(startTime, stopTime);
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            operationDto.setDuration(String.format("%02d.%02d.%02d", hours, minutes, seconds));
        } else {
            operationDto.setDuration("Нет данных");
        }

        return operationDto;
    }*/

    private OperationDto createAggregatedOperationDto(List<PppOperation> operations, String transaction) {
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

        // Вычисляем общую длительность
        Duration totalDuration = operations.stream()
                .filter(op -> op.getStartTime() != null && op.getStopTime() != null)
                .map(op -> Duration.between(op.getStartTime(), op.getStopTime()))
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
        operationDto.setNorm(convertToNormDto(pppNormsRepository.findByOperationNormName(anyOperation.getOperationType())
                .orElseThrow(() -> new EntityNotFoundException("Norm not found with name: " + anyOperation.getOperationType() + " in transaction: " + transaction))));

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
    
        // Форматируем общую длительность
        long hours = totalDuration.toHours();
        long minutes = totalDuration.toMinutesPart();
        long seconds = totalDuration.toSecondsPart();
        operationDto.setDuration(String.format("%02d.%02d.%02d", hours, minutes, seconds));

        return operationDto;
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