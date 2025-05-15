package back3.project.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "specialization") 
public class PppEmployees {
    @Id
    @Column(name = "id")
    private Long employeesId;

    @Column(name = "name")
    private String employeesName;

    @Column(name = "specialization")
    private String employeesSpecialization;

}
