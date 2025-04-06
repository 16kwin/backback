package back3.project.service;

import back3.project.dto.EmployeePerformanceDto;
import back3.project.dto.OperationDto;
import back3.project.entity.PppEmployees;
import back3.project.repository.PppEmployeesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class EmployeePerformanceService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeePerformanceService.class);

    private final PppEmployeesRepository pppEmployeesRepository;
    private final OperationService operationService;

    public List<EmployeePerformanceDto> getEmployeePerformances() {
        logger.info("getEmployeePerformances() called");
        List<EmployeePerformanceDto> employeePerformanceDtos = new ArrayList<>();

        // 1. Получить список всех сотрудников
        List<PppEmployees> employees = pppEmployeesRepository.findAll();

        // 2. Получить все агрегированные операции
        List<OperationDto> allAggregatedOperations = operationService.getAllAggregatedOperations();

        for (PppEmployees employee : employees) {
            // 3. Для каждого сотрудника:
            EmployeePerformanceDto employeePerformanceDto = new EmployeePerformanceDto();
            employeePerformanceDto.setEmployeeId(employee.getEmployeesId());
            employeePerformanceDto.setEmployeeName(employee.getEmployeesName());
            employeePerformanceDto.setEmployeeSpecialization(employee.getEmployeesSpecialization());

            AtomicLong totalOperations = new AtomicLong(0);
            AtomicLong onTimeOperations = new AtomicLong(0);

            // 4. Фильтруем операции, относящиеся к текущему сотруднику
            allAggregatedOperations.stream()
                    .filter(operationDto -> operationDto.getEmployee().getEmployeesId().equals(employee.getEmployeesId()))
                    .forEach(operationDto -> {
                        totalOperations.incrementAndGet();
                        if (operationDto.getIsTimeExceedsNorm()) {
                            onTimeOperations.incrementAndGet();
                        }
                    });

            employeePerformanceDto.setTotalOperations(totalOperations.get());
            employeePerformanceDto.setOnTimeOperations(onTimeOperations.get());

            // 5. Вычислить процент операций, выполненных в срок
            double onTimePercentage = 0.0;
            if (totalOperations.get() > 0) {
                onTimePercentage = (double) onTimeOperations.get() / totalOperations.get() * 100;
            }
            employeePerformanceDto.setOnTimePercentage(onTimePercentage);

            employeePerformanceDtos.add(employeePerformanceDto);
        }

        logger.info("Returning {} employee performances", employeePerformanceDtos.size());
        return employeePerformanceDtos;
    }
}