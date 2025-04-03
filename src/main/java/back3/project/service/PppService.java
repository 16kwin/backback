package back3.project.service;

import back3.project.dto.OperationDto;
import back3.project.dto.OperationTime;
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

                    List<OperationTime> operationTimes = interOperationTimeService.calculateTimeDifferences(operationDtos);
                    pppDto.setOperationTimes(operationTimes);

                    // Calculate total duration sum
                    String totalDurationSum = calculateTotalDurationSum(operationDtos);
                    pppDto.setTotalDurationSum(totalDurationSum);

                    // Calculate total problems norm hours
                    Double totalProblemsNormHours = calculateTotalProblemsNormHours(operationDtos);
                    pppDto.setTotalProblemsNormHours(totalProblemsNormHours);

                    // Calculate percentage
                    pppDto.setCompletionPercentage(calculateCompletionPercentage(ppp.getPlanPpp(), totalDurationSum));

                    //Calculate positive inter-operation time sum
                    String positiveInterOperationTimeSum = calculatePositiveInterOperationTimeSum(operationTimes);
                    pppDto.setPositiveInterOperationTimeSum(positiveInterOperationTimeSum);
                    
                    // Calculate total sum
                    String totalSum = calculateAndFormatTotalSum(positiveInterOperationTimeSum, totalDurationSum, totalProblemsNormHours);
                    pppDto.setTotalSum(totalSum);

                    // Calculate forecast dates
                    LocalDate planDateStart = ppp.getPlanDateStart();
                    List<ForecastDateDto> forecastDatesPlan = forecastDateService.calculateForecastDates(planDateStart, operationDtos);
                    pppDto.setForecastDatesPlan(forecastDatesPlan);

                    List<ForecastDateDto> forecastDatesStart = forecastDateStartService.calculateForecastDates(ppp.getFactDateStart(), ppp.getForecastDateStart(), operationDtos);
                    pppDto.setForecastDatesStart(forecastDatesStart);

                    //  Calculate extended transport position date
                    pppDto.setExtendedTransportPositionDate(calculateExtendedTransportPositionDate(forecastDatesStart, operationDtos));
                    pppDto.setExtendedTransportPositionDatePlan(calculateExtendedTransportPositionDatePlan(forecastDatesPlan, operationDtos));

                    return pppDto;
                })
                .collect(Collectors.toList());

        PppListDto pppListDto = new PppListDto();
        pppListDto.setPpps(pppDtos);

        return pppListDto;
    }

    private Double calculateCompletionPercentage(Long planPpp, String totalDurationSum) {
        if (planPpp == null || totalDurationSum == null || totalDurationSum.isEmpty() || totalDurationSum.equals("00:00:00")) {
            return 0.0;
        }
        // Преобразуем totalDurationSum in hours
        double totalDurationHours = parseTotalDurationSumToHours(totalDurationSum);
        // Убедитесь, что totalDurationHours также не равно нулю, чтобы избежать деления на ноль.
        if (totalDurationHours == 0) {
            return 0.0;
        }
        // отношение фактического времени к плановому
        double percentage = ((double) planPpp * 8) / totalDurationHours * 100;
        return (double) Math.round(percentage);
    }

    // Переводим totalDurationSum in hours
    private double parseTotalDurationSumToHours(String totalDurationSum) {
        String[] parts = totalDurationSum.split(":");
        if (parts.length != 3) {
            // Если формат строки неправильный, возвращаем 0.0 чтоб избежать ошибки
            System.err.println("Неправильный формат времени: " + totalDurationSum);
            return 0.0;
        }
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            return hours + (minutes / 60.0) + (seconds / 3600.0);
        } catch (NumberFormatException e) {
            System.err.println("Ошибка при парсинге времени: " + totalDurationSum);
            return 0.0;
        }
    }

    // Считаем totalProblemsNormHours
    private Double calculateTotalProblemsNormHours(List<OperationDto> operationDtos) {
        if (operationDtos == null || operationDtos.isEmpty()) {
            return 0.0;
        }

        return operationDtos.stream()
                .map(OperationDto::getProblemsNormHours)
                .filter(Objects::nonNull)
                .reduce(0.0, Double::sum);
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
                .reduce(Duration.ZERO, Duration::plus);
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

        ForecastDateDto transportPositionDto = forecastDates.stream()
                .filter(dto -> dto != null && "Транспортное положение".equals(dto.getOperationName()))
                .findFirst()
                .orElse(null);

        if (transportPositionDto == null) {
            return null;
        }

        LocalDate transportPositionDate = transportPositionDto.getForecastDate();
        OperationDto transportPositionOperation = operationDtos.stream()
                .filter(dto -> dto != null && "Транспортное положение".equals(dto.getOperationType()))
                .findFirst()
                .orElse(null);

        if (transportPositionOperation == null) {
            return null;
        }

        Double transportPositionNorm = transportPositionOperation.getOptionNorm();
        LocalDateTime transportPositionDateTime = transportPositionDate.atTime(WORKDAY_START);
        long daysToAdd = (long) Math.floor(transportPositionNorm / 8);
        double remainingHours = transportPositionNorm % 8;
        LocalDateTime extendedDateTime = transportPositionDateTime.plusDays(daysToAdd).plusHours((long) remainingHours);
        extendedDateTime = adjustToWorkday(extendedDateTime);

        return extendedDateTime.toLocalDate();
    }

    private LocalDateTime adjustToWorkday(LocalDateTime dateTime) {
        if (dateTime.toLocalTime().isAfter(WORKDAY_END)) {
            long diffBetweenEndWorkAndCurrent = Duration.between(dateTime.toLocalTime(), WORKDAY_END).toHours();
            dateTime = dateTime.plusDays(1).with(WORKDAY_START);
            dateTime = dateTime.plusHours(diffBetweenEndWorkAndCurrent);
        }

        while (dateTime.getDayOfWeek() == DayOfWeek.SATURDAY || dateTime.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dateTime = dateTime.plusDays(1).with(WORKDAY_START);
        }
        return dateTime;
    }

    private String calculatePositiveInterOperationTimeSum(List<OperationTime> operationTimes) {
        if (operationTimes == null || operationTimes.isEmpty()) {
            return "00:00:00";
        }
        long totalSeconds = 0;       //Используем Long для хранения общего количества секунд
        for (OperationTime operationTime : operationTimes) {
            if (operationTime == null) continue;                 //Пропускаем если operationTime == null
            String timeDifference = operationTime.getTimeDifference();  //Получаем строку timeDifference
            if (timeDifference == null) continue;              //Пропускаем если  timeDifference == null
            long seconds = parseToSeconds(timeDifference);                    //Получаем секунды из Duration
            if (seconds > 0) {         //Суммируем общее количество секунд
                totalSeconds += seconds;
            }
        }
        return formatTime(totalSeconds);
    }

    private long parseToSeconds(String time) {
        String[] parts = time.split(":");
        if (parts.length != 3) {
            return 0;
        }
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

   private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

      private String calculateAndFormatTotalSum(String positiveInterOperationTimeSum, String totalDurationSum, Double totalProblemsNormHours) {
        long totalSeconds = 0;

        // Складываем positiveInterOperationTimeSum
        if (positiveInterOperationTimeSum != null && !positiveInterOperationTimeSum.isEmpty()) {
            totalSeconds += parseToSeconds(positiveInterOperationTimeSum);
        }

        // Складываем totalDurationSum
        if (totalDurationSum != null && !totalDurationSum.isEmpty()) {
            totalSeconds += parseToSeconds(totalDurationSum);
        }

        // Складываем totalProblemsNormHours (convert to seconds)
        if (totalProblemsNormHours != null) {
            totalSeconds += (long) (totalProblemsNormHours * 3600);  // Предполагаем, что totalProblemsNormHours в часах
        }

        return formatTime(totalSeconds);
    }
}