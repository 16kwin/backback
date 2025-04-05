package back3.project.repository;

import back3.project.entity.PppNorms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PppNormsRepository extends JpaRepository<PppNorms, String> {
    Optional<PppNorms> findByOperationNormName(String operationNormName);

    List<PppNorms> findByCategory(String category); // Добавлен метод findByCategory
    //Другие методы(если есть)
    List<PppNorms> findByOperationType(String operationType);
}