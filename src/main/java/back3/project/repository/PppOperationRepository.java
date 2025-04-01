package back3.project.repository;

import back3.project.entity.PppOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PppOperationRepository extends JpaRepository<PppOperation, Long> {
    List<PppOperation> findByTransactionAndCategory(String transaction, String category);
    List<PppOperation> findByTransaction(String transaction);
}