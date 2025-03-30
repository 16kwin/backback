package back3.project.repository;

import back3.project.entity.PppNorms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface PppNormsRepository extends JpaRepository<PppNorms, String> {
    Optional<PppNorms> findByOperationNormName(String operationNormName);
}