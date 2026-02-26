package com.ainativeos.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "template_version")
public class TemplateVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, length = 128)
    private String templateId;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "category", nullable = false, length = 128)
    private String category;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "template_json", nullable = false, columnDefinition = "TEXT")
    private String templateJson;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTemplateJson() {
        return templateJson;
    }

    public void setTemplateJson(String templateJson) {
        this.templateJson = templateJson;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
