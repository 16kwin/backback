package back3.project.repository;

import back3.project.entity.Problems;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProblemsRepository extends JpaRepository<Problems, Long> {
    List<Problems> findByTransactionAndIdEmployee(String transaction, Long employeeId);
}