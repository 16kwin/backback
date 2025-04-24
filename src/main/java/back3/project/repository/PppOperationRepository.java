package back3.project.repository;

import back3.project.entity.PppOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PppOperationRepository extends JpaRepository<PppOperation, Long> {
    List<PppOperation> findByTransaction(String transaction);

    @Query("SELECT po FROM PppOperation po WHERE po.transaction = :transaction AND po.employeesId IS NOT NULL")
    List<PppOperation> findByTransactionAndEmployeesIdIsNotNull(@Param("transaction") String transaction);

    List<PppOperation> findByEmployeesIdIsNotNull();

    List<PppOperation> findByEmployeesIdAndStartTimeBeforeAndStopTimeAfter(Long employeesId, LocalDateTime endTime, LocalDateTime startTime);

    List<PppOperation> findByTransactionAndEmployeesIdIsNotNullAndOperationType(String transaction,
            String operationType);
}