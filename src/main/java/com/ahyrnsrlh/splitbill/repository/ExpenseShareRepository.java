package com.ahyrnsrlh.splitbill.repository;

import com.ahyrnsrlh.splitbill.entity.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {

    @Query("SELECT es FROM ExpenseShare es JOIN FETCH es.participant WHERE es.expense.group.id = :groupId")
    List<ExpenseShare> findByGroupIdWithParticipant(@Param("groupId") Long groupId);
}
