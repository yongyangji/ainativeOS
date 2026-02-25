package com.ainativeos.reconcile;

import com.ainativeos.persistence.entity.DesiredStateJobEntity;
import com.ainativeos.persistence.repository.DesiredStateJobRepository;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.RuntimeCommandDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 后台收敛控制器（事件驱动/定时驱动）。
 */
@Component
public class DesiredStateReconcileController {

    private final DesiredStateJobRepository desiredStateJobRepository;
    private final DesiredStateJobService desiredStateJobService;
    private final RuntimeCommandDispatcher runtimeCommandDispatcher;

    public DesiredStateReconcileController(
            DesiredStateJobRepository desiredStateJobRepository,
            DesiredStateJobService desiredStateJobService,
            RuntimeCommandDispatcher runtimeCommandDispatcher
    ) {
        this.desiredStateJobRepository = desiredStateJobRepository;
        this.desiredStateJobService = desiredStateJobService;
        this.runtimeCommandDispatcher = runtimeCommandDispatcher;
    }

    @Scheduled(fixedDelayString = "${ainativeos.reconcile.controller-interval-ms:5000}")
    @Transactional
    public void reconcileLoop() {
        List<DesiredStateJobEntity> jobs = desiredStateJobRepository
                .findTop20ByStatusInAndNextRunAtBeforeOrderByNextRunAtAsc(List.of("ACTIVE"), Instant.now());
        for (DesiredStateJobEntity job : jobs) {
            DesiredStateJobPayload payload = desiredStateJobService.parsePayload(job);
            if (payload == null) {
                job.setStatus("FAILED");
                job.setLastMessage("invalid payload json");
                job.setUpdatedAt(Instant.now());
                desiredStateJobRepository.save(job);
                continue;
            }

            CommandExecutionResult applyResult = runtimeCommandDispatcher.execute(
                    payload.runtimeParams(),
                    payload.applyCommand(),
                    payload.timeoutSeconds()
            );
            if (!applyResult.success()) {
                job.setFailCount(job.getFailCount() + 1);
                job.setLastMessage("apply failed: " + summarize(applyResult.stderr(), applyResult.error()));
                if (job.getFailCount() >= 5) {
                    job.setStatus("FAILED");
                }
                job.setNextRunAt(Instant.now().plusMillis(payload.intervalMs()));
                job.setUpdatedAt(Instant.now());
                desiredStateJobRepository.save(job);
                continue;
            }

            CommandExecutionResult verifyResult = runtimeCommandDispatcher.execute(
                    payload.runtimeParams(),
                    payload.verifyCommand(),
                    payload.timeoutSeconds()
            );
            if (verifyResult.success()) {
                job.setFailCount(0);
                job.setStatus("ACTIVE");
                job.setLastMessage("converged and continue monitoring");
            } else {
                job.setFailCount(job.getFailCount() + 1);
                job.setLastMessage("verify failed: " + summarize(verifyResult.stderr(), verifyResult.error()));
                if (job.getFailCount() >= 5) {
                    job.setStatus("FAILED");
                }
            }
            job.setNextRunAt(Instant.now().plusMillis(payload.intervalMs()));
            job.setUpdatedAt(Instant.now());
            desiredStateJobRepository.save(job);
        }
    }

    private String summarize(String message, String fallback) {
        String content = (message == null || message.isBlank()) ? fallback : message;
        if (content == null || content.isBlank()) {
            return "no output";
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "..." : compact;
    }
}

