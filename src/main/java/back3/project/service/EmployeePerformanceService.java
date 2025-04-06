package back3.project.service;

import back3.project.dto.EmployeePerformanceDto;
import back3.project.dto.OperationDto;
import back3.project.entity.PppEmployees;
import back3.project.repository.PppEmployeesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


@Service
@RequiredArgsConstructor
public class EmployeePerformanceService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeePerformanceService.class);

    private final PppEmployeesRepository pppEmployeesRepository;
    private final OperationService operationService;

    private static final List<String> ALLOWED_SPECIALIZATIONS = Arrays.asList(
            "Механик",
            "Электрик",
            "Электронщик",
            "Комплектация",
            "Технолог"
    );

    public List<EmployeePerformanceDto> getEmployeePerformances() {
        logger.info("getEmployeePerformances() called");
        List<EmployeePerformanceDto> employeePerformanceDtos = new ArrayList<>();

        // 1. Получить список всех сотрудников
        List<PppEmployees> employees = pppEmployeesRepository.findAll();

        // 2. Отфильтровать список сотрудников по специальности
        List<PppEmployees> filteredEmployees = employees.stream()
                .filter(employee -> ALLOWED_SPECIALIZATIONS.contains(employee.getEmployeesSpecialization()))
                .toList();

        // 3. Получить все агрегированные операции
        List<OperationDto> allAggregatedOperations = operationService.getAllAggregatedOperations();

        for (PppEmployees employee : filteredEmployees) {
            // 4. Для каждого сотрудника:
            EmployeePerformanceDto employeePerformanceDto = new EmployeePerformanceDto();
            employeePerformanceDto.setEmployeeId(employee.getEmployeesId());
            employeePerformanceDto.setEmployeeName(employee.getEmployeesName());
            employeePerformanceDto.setEmployeeSpecialization(employee.getEmployeesSpecialization());

            AtomicLong totalOperations = new AtomicLong(0);
            AtomicLong onTimeOperations = new AtomicLong(0);
            AtomicReference<Double> totalTimeSpentInSeconds = new AtomicReference<>(0.0); //  Время в секундах
            AtomicReference<Double> totalNormInSeconds = new AtomicReference<>(0.0); //  Сумма operationNorm и optionNorm в секундах

            // 5. Фильтруем операции, относящиеся к текущему сотруднику
            allAggregatedOperations.stream()
                    .filter(operationDto -> operationDto.getEmployee().getEmployeesId().equals(employee.getEmployeesId()))
                    .forEach(operationDto -> {
                        totalOperations.incrementAndGet();
                        if (operationDto.getIsTimeExceedsNorm()) {
                            onTimeOperations.incrementAndGet();
                        }
                        Double duration = convertTimeToSeconds(operationDto.getTotalDuration()); // Преобразуем строку в секунды
                        totalTimeSpentInSeconds.updateAndGet(v -> v + (duration != null ? duration : 0)); //  Суммируем время

                        // Суммируем operationNorm и optionNorm в секундах
                        AtomicReference<Double> operationNormRef = new AtomicReference<>(0.0);
                        try {
                            NumberFormat nf = NumberFormat.getInstance(Locale.getDefault());
                            operationNormRef.set(nf.parse(operationDto.getNorm().getOperationNorm()).doubleValue());
                        } catch (ParseException e) {
                            logger.warn("Не удалось преобразовать operationNorm в число для операции: {}", operationDto.getOperationId());
                        }

                        AtomicReference<Double> optionNormRef = new AtomicReference<>(0.0);
                        optionNormRef.set(operationDto.getOptionNorm() != null ? operationDto.getOptionNorm() : 0.0);

                        //Преобразуем в секунды, если они в часах
                        operationNormRef.set(operationNormRef.get() * 3600);
                        optionNormRef.set(optionNormRef.get() * 3600);

                        totalNormInSeconds.updateAndGet(v -> v + operationNormRef.get() + optionNormRef.get());
                    });

            employeePerformanceDto.setTotalOperations(totalOperations.get());
            employeePerformanceDto.setOnTimeOperations(onTimeOperations.get());
            employeePerformanceDto.setTotalTimeSpent(convertSecondsToTime(totalTimeSpentInSeconds.get())); //  Преобразуем в HH:mm:ss
            employeePerformanceDto.setTotalNorm(convertSecondsToTime(totalNormInSeconds.get())); // Преобразуем в HH:mm:ss в String

            // Вычисляем процент выполнения нормы
            double normPercentage = 0.0;
            if (totalNormInSeconds.get() > 0) {
                normPercentage = (totalTimeSpentInSeconds.get() / totalNormInSeconds.get()) * 100;
            }
            employeePerformanceDto.setNormPercentage(String.format("%.2f", normPercentage)); // Форматируем до 2 знаков после запятой

            // 6. Вычислить процент операций, выполненных в срок
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

    private Double convertTimeToSeconds(String time) {
        String[] parts = time.split(":");
        if (parts.length != 3) {
            return 0.0; // Или выбросить исключение, если формат неправильный
        }
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            return (double) (hours * 3600 + minutes * 60 + seconds);
        } catch (NumberFormatException e) {
            return 0.0; // Или выбросить исключение, если не удалось преобразовать
        }
    }

    private String convertSecondsToTime(Double totalSeconds) {
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}