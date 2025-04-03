package back3.project.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PppDto {
    private String transaction;
    private String status;
    private Long planPpp;
    private LocalDate planDateStart;
    private LocalDate forecastDateStart;
    private LocalDate factDateStart;
    private LocalDate planDateStop;
    private LocalDate forecastDateStop;
    private LocalDate factDateStop;
    private LocalDate planDateShipment;
    private LocalDate forecastDateShipment;
    private LocalDate factDateShipment;
    private List<OperationDto> operations;
    private List<OperationTime> operationTimes;
    private List<ForecastDateDto> forecastDatesPlan;
    private List<ForecastDateDto> forecastDatesStart;
    private LocalDate extendedTransportPositionDate; 
    private LocalDate extendedTransportPositionDatePlan;
    private String totalDurationSum;// Change this line
    private Double totalProblemsNormHours; 
    private Double completionPercentage;
    private String positiveInterOperationTimeSum;
    private String totalSum;
}