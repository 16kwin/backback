package back3.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "work_report_ppp") 
public class PppOperation {
    @Id
    @Column(name = "id")
    private Long operationId;

    @Column(name = "transaction")
    private String transaction;

    @Column(name = "stage_ppp")
    private String operationType;
    @Column(name = "start_work")
    private LocalDateTime startTime;
    @Column(name = "stop_work")
    private LocalDateTime stopTime;
    @Column(name = "employee")
    private Long employeesId;

}
