package com.antiam.service;

import com.antiam.domain.IdentitySyncJob;
import com.antiam.repository.IdentitySyncJobRepository;
import com.antiam.repository.IdentitySyncRunRepository;
import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdentitySyncScheduler {

    private final IdentitySyncJobRepository syncJobs;
    private final IdentitySyncRunRepository syncRuns;
    private final IdentitySourceService identitySources;

    @Scheduled(fixedDelayString = "${iam.identity-sync.scheduler-delay-ms:60000}")
    public void runDueJobs() {
        Instant now = Instant.now();
        syncJobs.findByEnabledTrue().stream()
            .filter(this::hasCron)
            .filter(job -> isDue(job, now))
            .forEach(job -> identitySources.runSyncJob(job.getId(), "identity-sync-scheduler"));
    }

    private boolean hasCron(IdentitySyncJob job) {
        return job.getCronExpression() != null && !job.getCronExpression().isBlank();
    }

    private boolean isDue(IdentitySyncJob job, Instant now) {
        try {
            CronExpression expression = CronExpression.parse(job.getCronExpression());
            Instant baseline = syncRuns.findTopBySyncJobIdOrderByStartedAtDesc(job.getId())
                .map(run -> run.getStartedAt() == null ? job.getCreatedAt() : run.getStartedAt())
                .orElse(job.getCreatedAt());
            if (baseline == null) {
                baseline = now.minusSeconds(60);
            }
            var next = expression.next(baseline.atZone(ZoneId.systemDefault()));
            return next != null && !next.toInstant().isAfter(now);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
