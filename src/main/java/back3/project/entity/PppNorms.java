package back3.project.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "operation_norm") 
public class PppNorms {
    @Id
    @Column(name = "work_ppp")
    private String operationNormName;

    @Column(name = "operation_option_norm")
    private String operationNorm;

    @Column(name = "type_mashine")
    private String operationType;
    @Column(name = "specialty")
    private String category;
}
