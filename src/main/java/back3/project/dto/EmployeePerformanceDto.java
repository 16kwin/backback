package back3.project.dto;

import lombok.Data;

@Data
public class EmployeePerformanceDto {

    private Long employeeId;
    private String employeeName;
    private String employeeSpecialization;
    private Long totalOperations;
    private Long onTimeOperations;
    private Double onTimePercentage;
    private String totalTimeSpent;
    private String totalNorm;
    private String normPercentage;
    private String workingHoursFund;
    private Double workingHoursFundUsage;
}