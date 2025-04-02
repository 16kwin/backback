package back3.project.service;
import back3.project.dto.OperationDto;
import back3.project.dto.PppDto;
import back3.project.dto.PppListDto;
import back3.project.dto.ForecastDateDto;
import back3.project.entity.Ppp;
import back3.project.repository.PppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PppService {

    private final PppRepository pppRepository;
    private final OperationService operationService;
    private final PppConversionService pppConversionService;
    private final InterOperationTimeService interOperationTimeService;
    private final ForecastDateService forecastDateService;
    private final ForecastDateStartService forecastDateStartService;

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 30);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 30);

    public PppListDto getAllPpps() {
        List<Ppp> ppps = pppRepository.findAll();

        List<PppDto> pppDtos = ppps.stream()
                .map(ppp -> {
                    PppDto pppDto = pppConversionService.convertToPppDto(ppp);
                    List<OperationDto> operationDtos = operationService.getAggregatedOperations(ppp.getTransaction());
                    pppDto.setOperations(operationDtos);
                    pppDto.setOperationTimes(interOperationTimeService.calculateTimeDifferences(operationDtos));

                    // Calculate total duration sum
                    String totalDurationSum = calculateTotalDurationSum(operationDtos);
                    pppDto.setTotalDurationSum(totalDurationSum);

                    // Calculate forecast dates
                    LocalDate planDateStart = ppp.getPlanDateStart();
                    List<ForecastDateDto> forecastDatesPlan = forecastDateService.calculateForecastDates(planDateStart, operationDtos);
                    pppDto.setForecastDatesPlan(forecastDatesPlan);

                    List<ForecastDateDto> forecastDatesStart = forecastDateStartService.calculateForecastDates(ppp.getFactDateStart(), ppp.getForecastDateStart(), operationDtos);
                    pppDto.setForecastDatesStart(forecastDatesStart);

                    //  Calculate extended transport position date
                    pppDto.setExtendedTransportPositionDate(calculateExtendedTransportPositionDate(forecastDatesStart, operationDtos));
                    pppDto.setExtendedTransportPositionDatePlan(calculateExtendedTransportPositionDatePlan(forecastDatesPlan, operationDtos)); // Calculate new variable

                    return pppDto;
                })
                .collect(Collectors.toList());

        PppListDto pppListDto = new PppListDto();
        pppListDto.setPpps(pppDtos);

        return pppListDto;
    }

    private String calculateTotalDurationSum(List<OperationDto> operationDtos) {
        if (operationDtos == null || operationDtos.isEmpty()) {
            return "00:00:00";
        }
    
        Duration totalDuration = operationDtos.stream()
            .map(OperationDto::getTotalDuration)
            .filter(Objects::nonNull)
            .map(durationString -> {
                try {
                    String[] parts = durationString.split(":");
                    long hours = Long.parseLong(parts[0]);
                    long minutes = Long.parseLong(parts[1]);
                    long seconds = Long.parseLong(parts[2]);
    
                    String formattedDurationString = String.format("PT%dH%dM%dS", hours, minutes, seconds);
                    System.out.println("Attempting to parse duration string: " + formattedDurationString);
    
                    return Duration.parse(formattedDurationString);
    
                } catch (DateTimeParseException | NumberFormatException e) {
                    System.err.println("Error parsing duration string: " + durationString);
                    System.err.println("Exception message: " + e.getMessage());
                    return Duration.ZERO;
                }
            })
            .reduce(Duration.ZERO, Duration::plus); // Суммируем все Duration
    
        long hours = totalDuration.toHours();
        long minutes = totalDuration.toMinutesPart();
        long seconds = totalDuration.toSecondsPart();
    
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private LocalDate calculateExtendedTransportPositionDate(List<ForecastDateDto> forecastDates, List<OperationDto> operationDtos) {
        return calculateExtendedTransportPositionDateInternal(forecastDates, operationDtos);
    }

    private LocalDate calculateExtendedTransportPositionDatePlan(List<ForecastDateDto> forecastDates, List<OperationDto> operationDtos) {
        return calculateExtendedTransportPositionDateInternal(forecastDates, operationDtos);
    }

    private LocalDate calculateExtendedTransportPositionDateInternal(List<ForecastDateDto> forecastDates, List<OperationDto> operationDtos) {
        if (forecastDates == null || forecastDates.isEmpty()) {
            return null;
        }

        //  1. Get transport position date from the forecastDates
        ForecastDateDto transportPositionDto = forecastDates.stream()
                .filter(dto -> dto != null && "Транспортное положение".equals(dto.getOperationName()))
                .findFirst()
                .orElse(null);

        if (transportPositionDto == null) {
            return null; // If transportPositionDto is null, return null
        }

        LocalDate transportPositionDate = transportPositionDto.getForecastDate();

        //  2. Get transport position norm
        OperationDto transportPositionOperation = operationDtos.stream()
                .filter(dto -> dto != null && "Транспортное положение".equals(dto.getOperationType()))
                .findFirst()
                .orElse(null);

        if (transportPositionOperation == null) {
            return null; // If transportPositionOperation is null, return null
        }

        Double transportPositionNorm = transportPositionOperation.getOptionNorm();

        // 3. Calculate extended transport position date

        LocalDateTime transportPositionDateTime = transportPositionDate.atTime(WORKDAY_START);
        long daysToAdd = (long) Math.floor(transportPositionNorm / 8);
        double remainingHours = transportPositionNorm % 8;

        LocalDateTime extendedDateTime  = transportPositionDateTime.plusDays(daysToAdd).plusHours((long) remainingHours);

        extendedDateTime = adjustToWorkday(extendedDateTime);

        return extendedDateTime.toLocalDate();
    }

    private LocalDateTime adjustToWorkday(LocalDateTime dateTime) {
        // Учет рабочего времени
        if (dateTime.toLocalTime().isAfter(WORKDAY_END)) {
            long diffBetweenEndWorkAndCurrent =  Duration.between(dateTime.toLocalTime(), WORKDAY_END).toHours();
            dateTime = dateTime.plusDays(1).with(WORKDAY_START);
            dateTime = dateTime.plusHours(diffBetweenEndWorkAndCurrent);
        }

        //  Учет выходных дней
        while (dateTime.getDayOfWeek() == DayOfWeek.SATURDAY || dateTime.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dateTime = dateTime.plusDays(1).with(WORKDAY_START);
        }

        return dateTime;
    }
}