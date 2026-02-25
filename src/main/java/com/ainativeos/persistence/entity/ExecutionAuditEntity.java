package com.ainativeos.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 执行审计实体。
 */
@Entity
@Table(name = "execution_audit")
public class ExecutionAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false, length = 128)
    private String goalId;

    @Column(name = "op_id", nullable = false, length = 128)
    private String opId;

    @Column(name = "op_type", nullable = false, length = 128)
    private String opType;

    @Column(name = "op_signature", nullable = false, length = 128)
    private String opSignature;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "provider", nullable = false, length = 128)
    private String provider;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

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

    public String getOpId() {
        return opId;
    }

    public void setOpId(String opId) {
        this.opId = opId;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getOpSignature() {
        return opSignature;
    }

    public void setOpSignature(String opSignature) {
        this.opSignature = opSignature;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

