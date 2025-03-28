package back3.project.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "operation_norm") 
public class PppNorms {
    @Id
    @Column(name = "name")
    private String operationNormName;

    @Column(name = "norm")
    private String operationNorm;

}
