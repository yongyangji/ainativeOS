package com.ainativeos.template;

import jakarta.validation.constraints.NotBlank;

public record TemplateRollbackRequest(@NotBlank String version) {
}
