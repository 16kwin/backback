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

    public List<EmployeePerformanceDto> getEmployeePerformances(Integer month, Integer year) {
        logger.info("getEmployeePerformances() called with month: {}, year: {}", month, year);
        List<EmployeePerformanceDto> employeePerformanceDtos = new ArrayList<>();

        // 1. Получить список всех сотрудников
        List<PppEmployees> employees = pppEmployeesRepository.findAll();

        // 2. Отфильтровать список сотрудников по специальности
        List<PppEmployees> filteredEmployees = employees.stream()
                .filter(employee -> ALLOWED_SPECIALIZATIONS.contains(employee.getEmployeesSpecialization()))
                .toList();

        // 3. Получить все агрегированные операции (возможно, потребуется изменить OperationService)
        List<OperationDto> allAggregatedOperations = operationService.getAllAggregatedOperations();

        // Фильтруем операции по месяцу, если месяц указан
        if (month != null && year != null) {
            allAggregatedOperations = allAggregatedOperations.stream()
                    .filter(operationDto -> operationDto.getStartTime() != null &&
                                           operationDto.getStartTime().getMonthValue() == month &&
                                           operationDto.getStartTime().getYear() == year)
                    .toList();
        } else if (year != null) {
            allAggregatedOperations = allAggregatedOperations.stream()
                    .filter(operationDto -> operationDto.getStartTime() != null &&
                                           operationDto.getStartTime().getYear() == year)
                    .toList();
        } else {
            logger.info("Month and year are null, returning data for the whole period.");
        }
        for (PppEmployees employee : filteredEmployees) {
            // 4. Для каждого сотрудника:
            EmployeePerformanceDto employeePerformanceDto = new EmployeePerformanceDto();
            employeePerformanceDto.setEmployeeId(employee.getEmployeesId());
            employeePerformanceDto.setEmployeeName(employee.getEmployeesName());
            employeePerformanceDto.setEmployeeSpecialization(employee.getEmployeesSpecialization());

            AtomicLong totalOperations = new AtomicLong(0);
            AtomicLong onTimeOperations = new AtomicLong(0);
            AtomicReference<Double> totalTimeSpentInSecondsRef = new AtomicReference<>(0.0); //  Время в секундах
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
                        totalTimeSpentInSecondsRef.updateAndGet(v -> v + (duration != null ? duration : 0)); //  Суммируем время

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
            employeePerformanceDto.setTotalTimeSpent(convertSecondsToTime(totalTimeSpentInSecondsRef.get())); //  Преобразуем в HH:mm:ss
            employeePerformanceDto.setTotalNorm(convertSecondsToTime(totalNormInSeconds.get())); // Преобразуем в HH:mm:ss в String

            // Вычисляем процент выполнения нормы
            double normPercentage = 0.0;
            if (totalNormInSeconds.get() > 0) {
                normPercentage = (totalNormInSeconds.get() / totalTimeSpentInSecondsRef.get()) * 100;
            }
            employeePerformanceDto.setNormPercentage(String.format("%.2f", normPercentage)); // Форматируем до 2 знаков после запятой

            // 6. Вычислить процент операций, выполненных в срок
            double onTimePercentage = 0.0;
            if (totalOperations.get() > 0) {
                onTimePercentage = (double) onTimeOperations.get() / totalOperations.get() * 100;
            }
            employeePerformanceDto.setOnTimePercentage(onTimePercentage);

            employeePerformanceDto.setWorkingHoursFund(getWorkingHoursFundForMonth(month));

            // Вычисляем использование фонда рабочего времени
            Double workingHoursFundInSeconds = convertTimeToSeconds(employeePerformanceDto.getWorkingHoursFund());
            Double totalTimeSpentInSeconds = convertTimeToSeconds(employeePerformanceDto.getTotalTimeSpent());

            double workingHoursFundUsage = 0.0;
            if (workingHoursFundInSeconds != null && workingHoursFundInSeconds > 0 && totalTimeSpentInSeconds != null) {
                workingHoursFundUsage = (double) Math.round((totalTimeSpentInSeconds / workingHoursFundInSeconds) * 100) / 100;
            }

            employeePerformanceDto.setWorkingHoursFundUsage(workingHoursFundUsage);

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

    private String getWorkingHoursFundForMonth(Integer month) {
        if (month == null) {
            return String.format("%04d:00:00", 1984); // Если месяц не указан, возвращаем годовое значение
        }

        int hours;
        switch (month) {
            case 1:  hours = 136; break; // Январь
            case 2:  hours = 152; break; // Февраль
            case 3:  hours = 160; break; // Март
            case 4:  hours = 176; break; // Апрель
            case 5:  hours = 144; break; // Май
            case 6:  hours = 168; break; // Июнь
            case 7:  hours = 184; break; // Июль
            case 8:  hours = 168; break; // Август
            case 9:  hours = 176; break; // Сентябрь
            case 10: hours = 184; break; // Октябрь
            case 11: hours = 152; break; // Ноябрь
            case 12: hours = 184; break; // Декабрь
            default: hours = 0; // Если месяц не указан или указан неверно
        }
        return String.format("%02d:00:00", hours); // Форматируем в HH:mm:ss
    }
}