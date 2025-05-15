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

    @Column(name = "type")
    private String operationType;

    @Column(name = "work")
    private String operationWork;

    @Column(name = "start")
    private LocalDateTime startTime;
    @Column(name = "stop")
    private LocalDateTime stopTime;
    @Column(name = "employees_id")
    private Long employeesId;

}
