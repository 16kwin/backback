package back3.project.service;

import back3.project.dto.EmployeeStatisticsDto;
import back3.project.dto.PppDto;
import back3.project.dto.PppListDto;
import back3.project.entity.PppEmployees;
import back3.project.repository.PppEmployeesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeStatisticsService {

    private final PppEmployeesRepository pppEmployeesRepository;
    private final OperationService operationService;
    private final PppService pppService;
    private final Logger logger = LoggerFactory.getLogger(EmployeeStatisticsService.class);

    private static final List<String> ALLOWED_SPECIALIZATIONS = List.of(
            "Механик",
            "Электрик",
            "Электронщик",
            "Комплектация",
            "Технолог"
    );

    public EmployeeStatisticsDto getEmployeeStatistics() {
        logger.info("getEmployeeStatistics() called");
        Instant start = Instant.now();

        List<PppEmployees> filteredEmployees = pppEmployeesRepository.findByEmployeesSpecializationIn(ALLOWED_SPECIALIZATIONS);
        Instant afterGetEmployees = Instant.now();
        logger.info("Time to get employees: " + Duration.between(start, afterGetEmployees).toMillis() + "ms");

        if (filteredEmployees == null || filteredEmployees.isEmpty()) {
            logger.warn("No employees found in the database.");
            return new EmployeeStatisticsDto();
        }

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
        Instant afterCountSpecializations = Instant.now();
        logger.info("Time to count specializations: " + Duration.between(afterGetEmployees, afterCountSpecializations).toMillis() + "ms");

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
        Instant afterCountBusy = Instant.now();
        logger.info("Time to count busy employees: " + Duration.between(afterCountSpecializations, afterCountBusy).toMillis() + "ms");

        // 1. Получаем все PppDto
        PppListDto pppListDto = pppService.getAllPpps();
        List<PppDto> allPpps = pppListDto.getPpps();
        Instant afterGetPpps = Instant.now();
        logger.info("Time to get PPPs: " + Duration.between(afterCountBusy, afterGetPpps).toMillis() + "ms");

        // 2. Фильтруем PppDto в статусе "В работе"
        List<PppDto> pppsInWorkList = allPpps.stream()
                .filter(ppp -> ppp.getStatus() != null && "В работе".equals(ppp.getStatus()))
                .collect(Collectors.toList());

        int machinesInWorkCount = pppsInWorkList.size();

        // 3. Считаем PppDto, успевающие и не успевающие в норму
        int machinesOnTimeCount = (int) pppsInWorkList.stream()
                .filter(ppp -> ppp.getCompletionPercentage() != null && ppp.getCompletionPercentage() >= 100)
                .count();

        int machinesLateCount = machinesInWorkCount - machinesOnTimeCount;
        Instant afterCalculateMachines = Instant.now();
        logger.info("Time to calculate machines: " + Duration.between(afterGetPpps, afterCalculateMachines).toMillis() + "ms");

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

        logger.info("Employee statistics calculated");
        Instant end = Instant.now();
        logger.info("getEmployeeStatistics() took " + Duration.between(start, end).toMillis() + "ms");
        return statistics;
    }

    private boolean isEmployeeBusyNow(PppEmployees employee, LocalDateTime now) {
        return operationService.isEmployeeBusyNow(employee.getEmployeesId(), now);
    }
}