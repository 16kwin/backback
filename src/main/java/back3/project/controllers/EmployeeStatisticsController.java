package back3.project.controllers;

import back3.project.dto.EmployeeStatisticsDto;
import back3.project.service.EmployeeStatisticsService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://194.87.56.253:3000") 
public class EmployeeStatisticsController {

    private final EmployeeStatisticsService employeeStatisticsService;

    @GetMapping("/employee-statistics")
    public EmployeeStatisticsDto getEmployeeStatistics() {
        return employeeStatisticsService.getEmployeeStatistics();
    }
}