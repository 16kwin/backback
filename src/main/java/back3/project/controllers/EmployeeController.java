package back3.project.controllers;

import back3.project.dto.EmployeePerformanceDto;
import back3.project.service.EmployeePerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://194.87.56.253:3000")
public class EmployeeController {

    private final EmployeePerformanceService employeePerformanceService;

    @GetMapping("/performance")
    public ResponseEntity<List<EmployeePerformanceDto>> getEmployeePerformances(@RequestParam(required = false) Integer month) {
        List<EmployeePerformanceDto> employeePerformances = employeePerformanceService.getEmployeePerformances(month);
        return ResponseEntity.ok(employeePerformances);
    }
}