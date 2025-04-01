package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.ForecastDateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ForecastDateStartService {

    // Определите последовательность операций
    private static final List<String> OPERATION_SEQUENCE = Arrays.asList(
            "Входной контроль",
            "Подключение",
            "Проверка механиком",
            "Проверка электронщиком",
            "Проверка технологом",
            "Выходной контроль",
            "Транспортное положение"
    );


    public List<ForecastDateDto> calculateForecastDates(LocalDate factDateStart, LocalDate forecastDateStart, List<OperationDto> operations) {
        LocalDate startDate = (factDateStart != null) ? factDateStart : forecastDateStart;
        List<ForecastDateDto> forecastDates = new ArrayList<>();
        LocalDate currentDate = startDate;
        OperationDto previousOperation = null;

        for (String operationType : OPERATION_SEQUENCE) {
            // Ищем операцию в списке operations
            OperationDto operation = findOperationByType(operations, operationType);

            if (operation == null) {
                System.out.println("Operation " + operationType + " not found, skipping");
                forecastDates.add(null); // Или можно создать специальный DTO с пометкой об отсутствии данных
                continue;
            }

            double totalNormValue = 0;

            if (previousOperation != null) {
                totalNormValue = Double.parseDouble(previousOperation.getNorm().getOperationNorm()) + previousOperation.getOptionNorm();
            }
            long daysToAdd;
            if (totalNormValue < 8) { // If total norm is less than or equal to 8 hours
                daysToAdd = 0; // Add only one day
            } else {
                daysToAdd = (long) (totalNormValue / 8); // Otherwise, calculate the number of days
            }
            if(operationType.equals("Входной контроль")){
                daysToAdd = 0;//первой дате + 1
            }

            LocalDate newDate = currentDate.plusDays(daysToAdd);

            ForecastDateDto forecastDateDto = new ForecastDateDto();
            forecastDateDto.setOperationName(operationType);
            forecastDateDto.setForecastDate(newDate);
            forecastDates.add(forecastDateDto);
            currentDate = newDate;
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