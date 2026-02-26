package com.ainativeos.api;

import com.ainativeos.api.dto.ExecutionSummaryResponse;
import com.ainativeos.capability.CapabilityRouter;
import com.ainativeos.api.dto.TraceEventResponse;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.health.HealthCheckService;
import com.ainativeos.persistence.entity.EventDeliveryEntity;
import com.ainativeos.persistence.entity.DesiredStateJobEntity;
import com.ainativeos.persistence.entity.GoalExecutionEntity;
import com.ainativeos.persistence.entity.GoalTraceEntity;
import com.ainativeos.plugin.PluginRegistryService;
import com.ainativeos.persistence.repository.DesiredStateJobRepository;
import com.ainativeos.persistence.repository.EventDeliveryRepository;
import com.ainativeos.persistence.repository.GoalExecutionRepository;
import com.ainativeos.persistence.repository.GoalTraceRepository;
import com.ainativeos.runtime.RuntimeCommandDispatcher;
import com.ainativeos.service.SemanticKernelService;
import com.ainativeos.template.TemplateExecutionRequest;
import com.ainativeos.template.TemplateRollbackRequest;
import com.ainativeos.template.TemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/goals")
/**
 * 目标控制器。
 * <p>
 * 对外提供 AI-Native OS 的核心 HTTP 接口，包括：
 * 1. 目标规划（plan）
 * 2. 目标执行（execute）
 * 3. 执行记录查询（executions）
 * 4. 执行轨迹查询（trace）
 * 5. 动态健康检查（health）
 */
public class GoalController {

    private final SemanticKernelService semanticKernelService;
    private final GoalExecutionRepository goalExecutionRepository;
    private final GoalTraceRepository goalTraceRepository;
    private final DesiredStateJobRepository desiredStateJobRepository;
    private final HealthCheckService healthCheckService;
    private final ObjectMapper objectMapper;
    private final RuntimeCommandDispatcher runtimeCommandDispatcher;
    private final CapabilityRouter capabilityRouter;
    private final PluginRegistryService pluginRegistryService;
    private final TemplateService templateService;
    private final EventDeliveryRepository eventDeliveryRepository;

    public GoalController(
            SemanticKernelService semanticKernelService,
            GoalExecutionRepository goalExecutionRepository,
            GoalTraceRepository goalTraceRepository,
            DesiredStateJobRepository desiredStateJobRepository,
            HealthCheckService healthCheckService,
            ObjectMapper objectMapper,
            RuntimeCommandDispatcher runtimeCommandDispatcher,
            CapabilityRouter capabilityRouter,
            PluginRegistryService pluginRegistryService,
            TemplateService templateService,
            EventDeliveryRepository eventDeliveryRepository
    ) {
        this.semanticKernelService = semanticKernelService;
        this.goalExecutionRepository = goalExecutionRepository;
        this.goalTraceRepository = goalTraceRepository;
        this.desiredStateJobRepository = desiredStateJobRepository;
        this.healthCheckService = healthCheckService;
        this.objectMapper = objectMapper;
        this.runtimeCommandDispatcher = runtimeCommandDispatcher;
        this.capabilityRouter = capabilityRouter;
        this.pluginRegistryService = pluginRegistryService;
        this.templateService = templateService;
        this.eventDeliveryRepository = eventDeliveryRepository;
    }

    @PostMapping("/plan")
    /**
     * 根据用户目标生成执行计划（不执行）。
     *
     * @param goalSpec 用户提交的目标规格
     * @return 规划结果，包含原子操作序列和期望状态
     */
    public GoalPlan plan(@Valid @RequestBody GoalSpec goalSpec) {
        return semanticKernelService.plan(goalSpec);
    }

    @PostMapping("/execute")
    /**
     * 规划并执行目标。
     * <p>
     * 内部流程：先调用 planner 生成 GoalPlan，再调用执行器进行执行与持久化。
     *
     * @param goalSpec 用户提交的目标规格
     * @return 执行结果（包含状态、失败对象、执行轨迹）
     */
    public GoalExecutionResult execute(@Valid @RequestBody GoalSpec goalSpec) {
        GoalPlan plan = semanticKernelService.plan(goalSpec);
        return semanticKernelService.execute(plan);
    }

    @GetMapping("/executions")
    /**
     * 查询执行记录。
     *
     * @param goalId 可选；传入时按目标 ID 过滤，不传返回最近执行记录
     * @return 执行摘要列表
     */
    public List<ExecutionSummaryResponse> executions(@RequestParam(required = false) String goalId) {
        List<GoalExecutionEntity> entities = (goalId == null || goalId.isBlank())
                ? goalExecutionRepository.findTop100ByOrderByCreatedAtDesc()
                : goalExecutionRepository.findTop50ByGoalIdOrderByCreatedAtDesc(goalId);

        return entities.stream().map(it -> new ExecutionSummaryResponse(
                it.getId(),
                it.getGoalId(),
                it.getStatus(),
                it.getSummary(),
                it.getPlannerVersion(),
                it.getCreatedAt()
        )).toList();
    }

    @GetMapping("/{goalId}/trace")
    /**
     * 查询指定目标的执行轨迹。
     *
     * @param goalId 目标 ID
     * @return 该目标的完整步骤级轨迹（按时间升序）
     */
    public List<TraceEventResponse> trace(@PathVariable String goalId) {
        List<GoalTraceEntity> entities = goalTraceRepository.findByGoalIdOrderByTimestampAsc(goalId);
        return entities.stream().map(it -> new TraceEventResponse(
                it.getId(),
                it.getGoalId(),
                it.getOpId(),
                it.getOpType(),
                it.getProvider(),
                it.getStatus(),
                it.getMessage(),
                it.getAttempt(),
                it.getTimestamp()
        )).toList();
    }

    @GetMapping("/{goalId}/replay")
    public Map<String, Object> replay(@PathVariable String goalId) {
        Optional<GoalExecutionEntity> latestExecutionOpt = goalExecutionRepository.findTop1ByGoalIdOrderByCreatedAtDesc(goalId);
        List<GoalTraceEntity> traces = goalTraceRepository.findByGoalIdOrderByTimestampAsc(goalId);

        Map<String, Object> planGraph = Map.of();
        if (latestExecutionOpt.isPresent()) {
            String planGraphJson = latestExecutionOpt.get().getPlanGraphJson();
            if (planGraphJson != null && !planGraphJson.isBlank()) {
                try {
                    planGraph = objectMapper.readValue(planGraphJson, new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) {
                    planGraph = Map.of("error", "plan_graph_deserialize_failed");
                }
            }
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("goalId", goalId);
        response.put("latestExecutionId", latestExecutionOpt.map(GoalExecutionEntity::getId).orElse(null));
        response.put("planGraph", planGraph);
        response.put("trace", traces.stream().map(it -> Map.<String, Object>of(
                "id", it.getId(),
                "opId", it.getOpId(),
                "opType", it.getOpType(),
                "provider", it.getProvider(),
                "status", it.getStatus(),
                "message", it.getMessage(),
                "attempt", it.getAttempt(),
                "timestamp", it.getTimestamp()
        )).toList());
        return response;
    }

    @GetMapping("/reconcile-jobs")
    /**
     * 查询后台持续收敛任务。
     *
     * @param goalId 可选；按目标过滤
     * @return 任务摘要列表
     */
    public List<Map<String, Object>> reconcileJobs(@RequestParam(required = false) String goalId) {
        List<DesiredStateJobEntity> jobs = (goalId == null || goalId.isBlank())
                ? desiredStateJobRepository.findTop100ByOrderByUpdatedAtDesc()
                : desiredStateJobRepository.findTop100ByGoalIdOrderByUpdatedAtDesc(goalId);
        return jobs.stream().map(job -> Map.<String, Object>of(
                "id", job.getId(),
                "goalId", job.getGoalId(),
                "status", job.getStatus(),
                "failCount", job.getFailCount(),
                "lastMessage", job.getLastMessage() == null ? "" : job.getLastMessage(),
                "nextRunAt", job.getNextRunAt(),
                "updatedAt", job.getUpdatedAt()
        )).toList();
    }

    @GetMapping("/health")
    /**
     * 动态健康检查。
     * <p>
     * 返回数据库、语义内核、能力层、自愈模块等组件的实时状态。
     *
     * @return 健康状态详情
     */
    public Map<String, Object> health() {
        return healthCheckService.check();
    }

    @GetMapping("/runtime-adapters")
    public List<Map<String, Object>> runtimeAdapters() {
        return runtimeCommandDispatcher.registeredAdapters();
    }

    @GetMapping("/capabilities")
    public List<Map<String, Object>> capabilities() {
        return capabilityRouter.capabilityDictionary();
    }

    @GetMapping("/plugins")
    public List<Map<String, Object>> plugins() {
        return pluginRegistryService.list().stream().map(plugin -> Map.<String, Object>of(
                "pluginId", plugin.pluginId(),
                "name", plugin.name() == null ? "" : plugin.name(),
                "version", plugin.version() == null ? "" : plugin.version(),
                "enabled", plugin.enabled(),
                "isolatedProcess", plugin.isolatedProcess(),
                "requiredCapabilities", plugin.requiredCapabilities() == null ? List.of() : plugin.requiredCapabilities(),
                "description", plugin.description() == null ? "" : plugin.description()
        )).toList();
    }

    @GetMapping("/templates")
    public List<Map<String, Object>> templates() {
        return templateService.listActiveTemplates();
    }

    @GetMapping("/templates/{templateId}/versions")
    public List<Map<String, Object>> templateVersions(@PathVariable String templateId) {
        return templateService.listVersions(templateId);
    }

    @PostMapping("/templates/execute")
    public GoalExecutionResult executeTemplate(@Valid @RequestBody TemplateExecutionRequest request) {
        return templateService.execute(request);
    }

    @PostMapping("/templates/{templateId}/rollback")
    public Map<String, Object> rollbackTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody TemplateRollbackRequest request
    ) {
        return templateService.rollback(templateId, request.version());
    }

    @GetMapping("/events")
    public List<Map<String, Object>> eventDeliveries(@RequestParam String goalId) {
        return eventDeliveryRepository.findTop100ByGoalIdOrderByCreatedAtDesc(goalId).stream()
                .map(this::toEventResponse)
                .toList();
    }

    private Map<String, Object> toEventResponse(EventDeliveryEntity it) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", it.getId());
        payload.put("goalId", it.getGoalId());
        payload.put("eventType", it.getEventType());
        payload.put("targetUrl", it.getTargetUrl());
        payload.put("success", it.isSuccess());
        payload.put("httpStatus", it.getHttpStatus());
        payload.put("errorMessage", it.getErrorMessage() == null ? "" : it.getErrorMessage());
        payload.put("createdAt", it.getCreatedAt());
        return payload;
    }
}
