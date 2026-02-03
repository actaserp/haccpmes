package mes.domain.repository;

import mes.domain.entity.CapIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CapIssueRepository extends JpaRepository<CapIssue, Integer> {


    Optional<CapIssue> findByCapNo(String capNumber);

    @Query(
            value = """
        SELECT "CapNo"
        FROM cap_issue
        WHERE "CapNo" LIKE CONCAT(:datePrefix, '%')
        ORDER BY "CapNo" DESC
        LIMIT 1
    """,
            nativeQuery = true
    )
    Optional<String> findMaxCapNoByDate(@Param("datePrefix") String datePrefix);

    void deleteByCapNo(String capNo);
}
