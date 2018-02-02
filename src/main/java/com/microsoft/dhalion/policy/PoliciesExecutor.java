/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *
 * This program is made available under the terms of the MIT License.
 * See the LICENSE file in the project root for more information.
 */

package com.microsoft.dhalion.policy;

import com.microsoft.dhalion.api.IHealthPolicy;
import com.microsoft.dhalion.detector.Symptom;
import com.microsoft.dhalion.diagnoser.Diagnosis;
import com.microsoft.dhalion.metrics.Measurement;
import com.microsoft.dhalion.resolver.Action;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class PoliciesExecutor {
  private static final Logger LOG = Logger.getLogger(PoliciesExecutor.class.getName());
  private final List<IHealthPolicy> policies;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  public PoliciesExecutor(List<IHealthPolicy> policies) {
    this.policies = policies;
  }

  public ScheduledFuture<?> start() {
    ScheduledFuture<?> future = executor.scheduleWithFixedDelay(() -> {
      // schedule the next execution cycle
      Duration nextScheduleDelay = policies.stream()
          .map(x -> x.getDelay())
          .min(Comparator.naturalOrder())
          .orElse(Duration.ofSeconds(10));

      if (nextScheduleDelay.toMillis() > 0) {
        try {
          LOG.info("Sleep (millis) before next policy execution cycle: " + nextScheduleDelay);
          TimeUnit.MILLISECONDS.sleep(nextScheduleDelay.toMillis());
        } catch (InterruptedException e) {
          LOG.warning("Interrupted while waiting for next policy execution cycle");
        }
      }

      for (IHealthPolicy policy : policies) {
        if (policy.getDelay().toMillis() > 0) {
          continue;
        }

        LOG.info("Executing Policy: " + policy.getClass().getSimpleName());
        Collection<Measurement> metrics = policy.executeSensors();
        // TODO update CacheState
        Collection<Symptom> symptoms = policy.executeDetectors(metrics);
        Collection<Diagnosis> diagnosis = policy.executeDiagnosers(symptoms);
        Collection<Action> actions = policy.executeResolvers(diagnosis);
        // TODO pretty print
        LOG.info(actions.toString());
      }

    }, 1, 1, TimeUnit.MILLISECONDS);

    return future;
  }

  public void destroy() {
    this.executor.shutdownNow();
  }
}
