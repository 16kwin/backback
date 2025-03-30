package back3.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationDto {
    private Long operationId;
    private String operationType;
    private String operationWork;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;
    private EmployeeDto employee;
    private NormDto norm;
    private String operationDuration;
    private String optionsDuration;
    private String totalDuration;
    private Double optionNorm;
    private Boolean isTimeExceedsNorm;
}