
package back3.project.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "faults") // Замените "your_table_name" на фактическое имя таблицы
public class Problems {

    @Id
    @Column(name = "id")
    private Long id; // "bigint" соответствует типу Long

    @Column(name = "transaction")
    private String transaction; // "text" соответствует типу String

    @Column(name = "id_employee")
    private Long idEmployee; // "bigint" соответствует типу Long

    @Column(name = "type")
    private String type; // "text" соответствует типу String

    @Column(name = "description")
    private String description; // "text" соответствует типу String

    @Column(name = "norm_hours")
    private Long normHours; // "bigint" соответствует типу Long

}