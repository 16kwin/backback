package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.PppDto;
import back3.project.dto.PppListDto;
import back3.project.entity.Ppp;
import back3.project.repository.PppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PppService {

    private final PppRepository pppRepository;
    private final OperationService operationService;
    private final PppConversionService pppConversionService;
    private final InterOperationTimeService interOperationTimeService;

    public PppListDto getAllPpps() {
        List<Ppp> ppps = pppRepository.findAll();

        List<PppDto> pppDtos = ppps.stream()
                .map(ppp -> {
                    PppDto pppDto = pppConversionService.convertToPppDto(ppp);
                    List<OperationDto> operationDtos = operationService.getAggregatedOperations(ppp.getTransaction());
                    pppDto.setOperations(operationDtos);
                    pppDto.setOperationTimes(interOperationTimeService.calculateTimeDifferences(operationDtos)); // Use the new service for calculation
                    return pppDto;
                })
                .collect(Collectors.toList());

        PppListDto pppListDto = new PppListDto();
        pppListDto.setPpps(pppDtos);

        return pppListDto;
    }
}