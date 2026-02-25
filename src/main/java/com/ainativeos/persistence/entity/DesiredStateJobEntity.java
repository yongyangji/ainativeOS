package com.ainativeos.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 声明式状态收敛任务实体。
 */
@Entity
@Table(name = "desired_state_job")
public class DesiredStateJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false, length = 128)
    private String goalId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "job_payload_json", nullable = false, columnDefinition = "TEXT")
    private String jobPayloadJson;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "last_message", length = 1000)
    private String lastMessage;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getGoalId() {
        return goalId;
    }

    public void setGoalId(String goalId) {
        this.goalId = goalId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getJobPayloadJson() {
        return jobPayloadJson;
    }

    public void setJobPayloadJson(String jobPayloadJson) {
        this.jobPayloadJson = jobPayloadJson;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

