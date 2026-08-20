package com.ahyrnsrlh.splitbill.repository;

import com.ahyrnsrlh.splitbill.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("SELECT e FROM Expense e JOIN FETCH e.paidBy WHERE e.group.id = :groupId")
    List<Expense> findByGroupIdWithPayer(@Param("groupId") Long groupId);
}
