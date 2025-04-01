package back3.project.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ForecastDateDto {
    private String operationName;
    private LocalDate forecastDate;
}