package back3.project.entity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
@Data
@Entity
@Table(name = "plan_ppp") 
public class Ppp {
    @Id
    @Column(name = "\"Machine\"")
    private String transaction;

    @Column(name = "\"Status_ppp\"")
    private String status;

    @Column(name = "\"Count_days_PPP\"")
    private Long planPpp;
    @Column(name = "\"Date_start_PPP_Plan\"")
    private LocalDate planDateStart;

    @Column(name = "\"Date_start_PPP_Forecast\"")
    private LocalDate forecastDateStart;

    @Column(name = "\"Date_start_PPP_Fact\"")
    private LocalDate factDateStart;

    @Column(name = "\"Date_end_PPP_Plan\"")
    private LocalDate planDateStop;

    @Column(name = "\"Date_end_PPP_Forecast\"")
    private LocalDate forecastDateStop;

    @Column(name = "\"Date_end_PPP_Fact\"")
    private LocalDate factDateStop;

    @Column(name = "\"Shipping_date_Plan\"")
    private LocalDate planDateShipment;

    @Column(name = "\"Shipping_date_Forecast\"")
    private LocalDate forecastDateShipment;

    @Column(name = "\"Shipping_date_Fact\"")
    private LocalDate factDateShipment;
}
