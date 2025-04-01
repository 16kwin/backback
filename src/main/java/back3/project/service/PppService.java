package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.PppDto;
import back3.project.dto.PppListDto;
import back3.project.dto.ForecastDateDto;
import back3.project.entity.Ppp;
import back3.project.repository.PppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PppService {

    private final PppRepository pppRepository;
    private final OperationService operationService;
    private final PppConversionService pppConversionService;
    private final InterOperationTimeService interOperationTimeService;
    private final ForecastDateService forecastDateService;
    private final ForecastDateStartService forecastDateStartService;  // Внедрили новый сервис

    public PppListDto getAllPpps() {
        List<Ppp> ppps = pppRepository.findAll();

        List<PppDto> pppDtos = ppps.stream()
                .map(ppp -> {
                    PppDto pppDto = pppConversionService.convertToPppDto(ppp);
                    List<OperationDto> operationDtos = operationService.getAggregatedOperations(ppp.getTransaction());
                    pppDto.setOperations(operationDtos);
                    pppDto.setOperationTimes(interOperationTimeService.calculateTimeDifferences(operationDtos));

                    // Calculate forecast dates
                    LocalDate planDateStart = ppp.getPlanDateStart();
                    List<ForecastDateDto> forecastDatesPlan = forecastDateService.calculateForecastDates(planDateStart, operationDtos);
                    pppDto.setForecastDatesPlan(forecastDatesPlan);

                     List<ForecastDateDto> forecastDatesStart = forecastDateStartService.calculateForecastDates(ppp.getFactDateStart(), ppp.getForecastDateStart(),operationDtos);
                    pppDto.setForecastDatesStart(forecastDatesStart);

                    return pppDto;
                })
                .collect(Collectors.toList());

        PppListDto pppListDto = new PppListDto();
        pppListDto.setPpps(pppDtos);

        return pppListDto;
    }
}