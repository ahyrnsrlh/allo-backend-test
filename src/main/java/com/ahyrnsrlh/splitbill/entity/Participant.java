package com.ahyrnsrlh.splitbill.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "participants",
       indexes = @Index(name = "idx_participant_group", columnList = "group_id"))
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private BillGroup group;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = Instant.now();
    }

    public Participant() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BillGroup getGroup() { return group; }
    public void setGroup(BillGroup group) { this.group = group; }
    public Instant getCreatedAt() { return createdAt; }
}
