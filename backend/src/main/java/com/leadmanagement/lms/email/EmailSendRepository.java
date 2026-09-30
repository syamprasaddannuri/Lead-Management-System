package com.leadmanagement.lms.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EmailSendRepository extends JpaRepository<EmailSend, UUID> {

    boolean existsBySourceActivityId(UUID sourceActivityId);

    long countByCategory(String category);

    long countByCreatedAtGreaterThanEqual(OffsetDateTime since);

    @Query("select e.senderLabel, count(e) from EmailSend e group by e.senderLabel order by count(e) desc")
    List<Object[]> countBySenderLabelAll();

    @Query("select e.senderLabel, count(e) from EmailSend e where e.createdAt >= :since "
            + "group by e.senderLabel order by count(e) desc")
    List<Object[]> countBySenderLabelSince(@Param("since") OffsetDateTime since);

    @Query("select e.category, count(e) from EmailSend e group by e.category order by count(e) desc")
    List<Object[]> countByCategoryAll();

    @Query("select e.category, count(e) from EmailSend e where e.createdAt >= :since "
            + "group by e.category order by count(e) desc")
    List<Object[]> countByCategorySince(@Param("since") OffsetDateTime since);
}
