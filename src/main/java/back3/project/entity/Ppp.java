package back3.project.entity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
@Data
@Entity
@Table(name = "ppp") 
public class Ppp {
    @Id
    @Column(name = "transaction")
    private String transaction;

    @Column(name = "status")
    private String status;

    @Column(name = "plan_ppp")
    private Long planPpp;
    @Column(name = "plan_date_start")
    private LocalDate planDateStart;

    @Column(name = "forecast_date_start")
    private LocalDate forecastDateStart;

    @Column(name = "fact_date_start")
    private LocalDate factDateStart;

    @Column(name = "plan_date_stop")
    private LocalDate planDateStop;

    @Column(name = "forecast_date_stop")
    private LocalDate forecastDateStop;

    @Column(name = "fact_date_stop")
    private LocalDate factDateStop;

    @Column(name = "plan_date_shipment")
    private LocalDate planDateShipment;

    @Column(name = "forecast_date_shipment")
    private LocalDate forecastDateShipment;

    @Column(name = "fact_date_shipment")
    private LocalDate factDateShipment;
}
