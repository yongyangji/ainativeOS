package com.ainativeos.api;

import com.ainativeos.api.dto.ExecutionSummaryResponse;
import com.ainativeos.api.dto.TraceEventResponse;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.health.HealthCheckService;
import com.ainativeos.persistence.entity.DesiredStateJobEntity;
import com.ainativeos.persistence.entity.GoalExecutionEntity;
import com.ainativeos.persistence.entity.GoalTraceEntity;
import com.ainativeos.persistence.repository.DesiredStateJobRepository;
import com.ainativeos.persistence.repository.GoalExecutionRepository;
import com.ainativeos.persistence.repository.GoalTraceRepository;
import com.ainativeos.service.SemanticKernelService;
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

    public GoalController(
            SemanticKernelService semanticKernelService,
            GoalExecutionRepository goalExecutionRepository,
            GoalTraceRepository goalTraceRepository,
            DesiredStateJobRepository desiredStateJobRepository,
            HealthCheckService healthCheckService
    ) {
        this.semanticKernelService = semanticKernelService;
        this.goalExecutionRepository = goalExecutionRepository;
        this.goalTraceRepository = goalTraceRepository;
        this.desiredStateJobRepository = desiredStateJobRepository;
        this.healthCheckService = healthCheckService;
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
}
