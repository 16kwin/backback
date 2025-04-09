package back3.project.service;

import back3.project.dto.EmployeeStatisticsDto;
import back3.project.dto.OperationDto;
import back3.project.dto.PppDto;
import back3.project.dto.PppListDto;
import back3.project.entity.PppEmployees;
import back3.project.repository.PppEmployeesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeStatisticsService {

    private final PppEmployeesRepository pppEmployeesRepository;
    private final OperationService operationService;
    private final PppService pppService; // Используем существующий PppService
    private final Logger logger = LoggerFactory.getLogger(EmployeeStatisticsService.class);

    private static final List<String> ALLOWED_SPECIALIZATIONS = List.of(
            "Механик",
            "Электрик",
            "Электронщик",
            "Комплектация",
            "Технолог"
    );

    public EmployeeStatisticsDto getEmployeeStatistics() {
        logger.info("Calculating employee statistics");

        List<PppEmployees> allEmployees = pppEmployeesRepository.findAll();

        if (allEmployees == null || allEmployees.isEmpty()) {
            logger.warn("No employees found in the database.");
            return new EmployeeStatisticsDto(); // Return empty statistics
        }

        // Фильтруем сотрудников по специализации
        List<PppEmployees> filteredEmployees = allEmployees.stream()
                .filter(employee -> ALLOWED_SPECIALIZATIONS.contains(employee.getEmployeesSpecialization()))
                .collect(Collectors.toList());

        long mechanicCount = filteredEmployees.stream()
                .filter(employee -> "Механик".equals(employee.getEmployeesSpecialization()))
                .count();

        long electricianCount = filteredEmployees.stream()
                .filter(employee -> "Электронщик".equals(employee.getEmployeesSpecialization()))
                .count();

        long technologistCount = filteredEmployees.stream()
                .filter(employee -> "Технолог".equals(employee.getEmployeesSpecialization()))
                .count();
                long electricCount = filteredEmployees.stream()
                .filter(employee -> "Электрик".equals(employee.getEmployeesSpecialization()))
                .count();
                long complectionCount = filteredEmployees.stream()
                .filter(employee -> "Комплектация".equals(employee.getEmployeesSpecialization()))
                .count();

        // Calculating busy counts
        LocalDateTime now = LocalDateTime.now();

        long mechanicBusyCount = filteredEmployees.stream()
                .filter(employee -> "Механик".equals(employee.getEmployeesSpecialization()))
                .filter(employee -> isEmployeeBusyNow(employee, now))
                .count();

        long electricianBusyCount = filteredEmployees.stream()
                .filter(employee -> "Электронщик".equals(employee.getEmployeesSpecialization()))
                .filter(employee -> isEmployeeBusyNow(employee, now))
                .count();

        long technologistBusyCount = filteredEmployees.stream()
                .filter(employee -> "Технолог".equals(employee.getEmployeesSpecialization()))
                .filter(employee -> isEmployeeBusyNow(employee, now))
                .count();
                long electricBusyCount = filteredEmployees.stream()
                .filter(employee -> "Электрик".equals(employee.getEmployeesSpecialization()))
                .filter(employee -> isEmployeeBusyNow(employee, now))
                .count();
                long complectionBusyCount = filteredEmployees.stream()
                .filter(employee -> "Комплектация".equals(employee.getEmployeesSpecialization()))
                .filter(employee -> isEmployeeBusyNow(employee, now))
                .count();

        // 1. Получаем все PppDto
        PppListDto pppListDto = pppService.getAllPpps(); // Используем существующий PppService
        List<PppDto> allPpps = pppListDto.getPpps(); // Извлекаем List<PppDto> из PppListDto

        // 2. Фильтруем PppDto в статусе "В работе"
        List<PppDto> pppsInWorkList = allPpps.stream()
                .filter(ppp -> ppp.getStatus() != null && "В работе".equals(ppp.getStatus())) //  Предполагаем, что status - строка
                .collect(Collectors.toList());

        int machinesInWorkCount = pppsInWorkList.size();

        // 3. Считаем PppDto, успевающие и не успевающие в норму
        int machinesOnTimeCount = (int) pppsInWorkList.stream()
                .filter(ppp -> ppp.getCompletionPercentage() != null && ppp.getCompletionPercentage() >= 100) // Предполагаем, что completionPercentage - Double
                .count();

        int machinesLateCount = machinesInWorkCount - machinesOnTimeCount;

        EmployeeStatisticsDto statistics = new EmployeeStatisticsDto();
        statistics.setMechanicCount((int) mechanicCount);
        statistics.setElectricianCount((int) electricianCount);
        statistics.setTechnologistCount((int) technologistCount);
        statistics.setElectricCount((int) electricCount);
        statistics.setComplectionCount((int) complectionCount);

        statistics.setMechanicBusyCount((int) mechanicBusyCount);
        statistics.setElectricianBusyCount((int) electricianBusyCount);
        statistics.setTechnologistBusyCount((int) technologistBusyCount);
        statistics.setElectricBusyCount((int) electricBusyCount);
        statistics.setComplectionBusyCount((int) complectionBusyCount);

        statistics.setMechanicFreeCount((int) (mechanicCount - mechanicBusyCount));
        statistics.setElectricianFreeCount((int) (electricianCount - electricianBusyCount));
        statistics.setTechnologistFreeCount((int) (technologistCount - technologistBusyCount));
        statistics.setElectricFreeCount((int) (electricCount - electricBusyCount));
        statistics.setComplectionFreeCount((int) (complectionCount - complectionBusyCount));

        statistics.setMachinesInWork(machinesInWorkCount);
        statistics.setMachinesOnTime(machinesOnTimeCount);
        statistics.setMachinesLate(machinesLateCount);

        logger.info("Employee statistics calculated: {}", statistics);
        return statistics;
    }

    private boolean isEmployeeBusyNow(PppEmployees employee, LocalDateTime now) {
        // 1. Получаем все агрегированные операции для текущего сотрудника
        List<OperationDto> employeeOperations = operationService.getAllAggregatedOperations().stream()
                .filter(op -> op.getEmployee() != null && op.getEmployee().getEmployeesId().equals(employee.getEmployeesId()))
                .collect(Collectors.toList());

        // 2. Проверяем, есть ли среди этих операций активные на данный момент
        return employeeOperations.stream().anyMatch(op -> {
            // Проверяем startTime и stopTime на null
            if (op.getStartTime() == null || op.getStopTime() == null) {
                logger.warn("startTime or stopTime is null for operationId: " + op.getOperationId());
                return false; // Считаем, что сотрудник не занят, если нет времени
            }

            LocalDateTime startTime = op.getStartTime(); // используем существующие LocalDateTime
            LocalDateTime stopTime = op.getStopTime();  // используем существующие LocalDateTime

            // Проверяем попадание текущей даты и времени в интервал
            return !now.isBefore(startTime) && !now.isAfter(stopTime);
        });
    }
}