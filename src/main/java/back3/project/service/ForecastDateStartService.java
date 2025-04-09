package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.ForecastDateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ForecastDateStartService {

    private static final List<String> OPERATION_SEQUENCE = Arrays.asList(
            "Входной контроль",
            "Подключение",
            "Проверка механиком",
            "Проверка электронщиком",
            "Проверка технологом",
            "Выходной контроль",
            "Транспортное положение"
    );

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 30);

    public List<ForecastDateDto> calculateForecastDates(LocalDate factDateStart, LocalDate forecastDateStart, List<OperationDto> operations) {
        // Use factDateStart if available, otherwise use forecastDateStart
        LocalDate startDate = (factDateStart != null) ? factDateStart : forecastDateStart;

        List<ForecastDateDto> forecastDates = new ArrayList<>();
        LocalDateTime currentDateTime = startDate.atTime(WORKDAY_START);

        OperationDto previousOperation = null;

        for (String operationType : OPERATION_SEQUENCE) {
            OperationDto operation = findOperationByType(operations, operationType);

            if (operation == null) {
                System.out.println("Operation " + operationType + " not found, skipping");
                forecastDates.add(null);
                continue;
            }

            double totalNormValue = 0;
            if (previousOperation != null) {
                totalNormValue = Double.parseDouble(previousOperation.getNorm().getOperationNorm()) + previousOperation.getOptionNorm();
            }

            if (operationType.equals("Входной контроль")) {
                totalNormValue = 0;
                currentDateTime = currentDateTime.plusDays(2); // Добавляем 2 дня для входного контроля
            }

            long daysToAdd = (long) Math.floor(totalNormValue / 8);
            double remainingHours = totalNormValue % 8;

            LocalDateTime endDateTime = currentDateTime.plusDays(daysToAdd).plusHours((long) remainingHours);

            // Учет выходных дней
            while (endDateTime.getDayOfWeek() == DayOfWeek.SATURDAY || endDateTime.getDayOfWeek() == DayOfWeek.SUNDAY) {
                endDateTime = endDateTime.plusDays(1).with(LocalTime.of(8, 30));
            }
            if (endDateTime.toLocalTime().isAfter(LocalTime.of(17, 30))) {
                endDateTime = endDateTime.plusDays(1).with(LocalTime.of(8, 30));
                while (endDateTime.getDayOfWeek() == DayOfWeek.SATURDAY || endDateTime.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    endDateTime = endDateTime.plusDays(1).with(LocalTime.of(8, 30));
                }
            }

            ForecastDateDto forecastDateDto = new ForecastDateDto();
            forecastDateDto.setOperationName(operationType);
            forecastDateDto.setForecastDate(endDateTime.toLocalDate());
            forecastDates.add(forecastDateDto);

            currentDateTime = endDateTime;
            previousOperation = operation;
        }

        return forecastDates;
    }

    private OperationDto findOperationByType(List<OperationDto> operations, String operationType) {
        for (OperationDto operation : operations) {
            if (operation.getOperationType().equals(operationType)) {
                return operation;
            }
        }
        return null;
    }
}