package com.ahyrnsrlh.splitbill.repository;

import com.ahyrnsrlh.splitbill.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findByGroupId(Long groupId);

    boolean existsByGroupIdAndNameIgnoreCase(Long groupId, String name);
}
