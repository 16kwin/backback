package back3.project.dto;

import lombok.Data;

@Data // Или @Getter, @Setter, @AllArgsConstructor, @NoArgsConstructor - в зависимости от ваших предпочтений
public class EmployeeStatisticsDto {
    private int mechanicCount;
    private int electricianCount;
    private int technologistCount;
    private int electricCount;
    private int complectionCount;
    private int mechanicBusyCount;
    private int electricianBusyCount;
    private int technologistBusyCount;
    private int electricBusyCount;
    private int complectionBusyCount;
    private int mechanicFreeCount;
    private int electricianFreeCount;
    private int technologistFreeCount;
    private int electricFreeCount;
    private int complectionFreeCount;
    private int machinesInWork;       // Общее количество станков в статусе "В работе"
    private int machinesOnTime;        // Количество станков, успевающих в норму
    private int machinesLate;
}
