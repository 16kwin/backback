package back3.project.repository;

import back3.project.entity.PppEmployees;
import org.springframework.data.jpa.repository.JpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface PppEmployeesRepository extends JpaRepository<PppEmployees, Long> {
    Logger logger = LoggerFactory.getLogger(PppEmployeesRepository.class);

    List<PppEmployees> findByEmployeesSpecializationIn(List<String> specializations);
}