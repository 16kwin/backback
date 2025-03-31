package back3.project.service;

import back3.project.dto.EmployeeDto;
import back3.project.dto.NormDto;
import back3.project.dto.PppDto;
import back3.project.entity.Ppp;
import back3.project.entity.PppEmployees;
import back3.project.entity.PppNorms;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PppConversionService {


    public EmployeeDto convertToEmployeeDto(PppEmployees employee) {
        EmployeeDto employeeDto = new EmployeeDto();
        employeeDto.setEmployeesId(employee.getEmployeesId());
        employeeDto.setEmployeesName(employee.getEmployeesName());
        employeeDto.setEmployeesSpecialization(employee.getEmployeesSpecialization());
        return employeeDto;
    }

    public NormDto convertToNormDto(PppNorms norm) {
        NormDto normDto = new NormDto();
        normDto.setOperationNormName(norm.getOperationNormName());
        normDto.setOperationNorm(norm.getOperationNorm());

        return normDto;
    }


    public PppDto convertToPppDto(Ppp ppp) {
        PppDto pppDto = new PppDto();
        pppDto.setTransaction(ppp.getTransaction());
        pppDto.setStatus(ppp.getStatus());
        pppDto.setPlanPpp(ppp.getPlanPpp() * 8);
        pppDto.setPlanDateStart(ppp.getPlanDateStart());
        pppDto.setForecastDateStart(ppp.getForecastDateStart());
        pppDto.setFactDateStart(ppp.getFactDateStart());
        pppDto.setPlanDateStop(ppp.getPlanDateStop());
        pppDto.setForecastDateStop(ppp.getForecastDateStop());
        pppDto.setFactDateStop(ppp.getFactDateStop());
        pppDto.setPlanDateShipment(ppp.getPlanDateShipment());
        pppDto.setForecastDateShipment(ppp.getForecastDateShipment());
        pppDto.setFactDateShipment(ppp.getFactDateShipment());
        //pppDto.setOperations(operationDtos); // This should be moved to PPP Service after getting aggregated operations

        //Move time calculaton to PppService
        return pppDto;
    }



}