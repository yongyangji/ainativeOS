package com.ainativeos.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "goal_execution")
public class GoalExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false, length = 128)
    private String goalId;

    @Column(name = "intent", nullable = false, length = 2000)
    private String intent;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    @Column(name = "failure_json", columnDefinition = "TEXT")
    private String failureJson;

    @Column(name = "planner_version", nullable = false, length = 128)
    private String plannerVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getGoalId() {
        return goalId;
    }

    public void setGoalId(String goalId) {
        this.goalId = goalId;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getFailureJson() {
        return failureJson;
    }

    public void setFailureJson(String failureJson) {
        this.failureJson = failureJson;
    }

    public String getPlannerVersion() {
        return plannerVersion;
    }

    public void setPlannerVersion(String plannerVersion) {
        this.plannerVersion = plannerVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
