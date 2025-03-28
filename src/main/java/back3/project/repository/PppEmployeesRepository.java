package back3.project.repository;

import back3.project.entity.PppEmployees;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PppEmployeesRepository extends JpaRepository<PppEmployees, Long> {
}