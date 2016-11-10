/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal.daemon;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.process.internal.daemon.health.memory.MaximumHeapHelper;
import org.gradle.process.internal.daemon.health.memory.MemoryInfo;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Simple max heap based worker daemon expiration strategy.
 */
public class WorkerDaemonSimpleMemoryExpiration {

    // Reasonable default threshold bounds: between 384M and 1G
    private static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024;
    private static final long MAX_THRESHOLD_BYTES = 1024 * 1024 * 1024;

    private final MemoryInfo memoryInfo;
    private final MaximumHeapHelper maximumHeapHelper;
    private final long memoryThresholdInBytes;

    public WorkerDaemonSimpleMemoryExpiration(MemoryInfo memoryInfo, double minFreeMemoryPercentage) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.memoryInfo = Preconditions.checkNotNull(memoryInfo);
        this.maximumHeapHelper = new MaximumHeapHelper(memoryInfo);
        this.memoryThresholdInBytes = Math.min(MAX_THRESHOLD_BYTES, Math.max(MIN_THRESHOLD_BYTES, (long) (memoryInfo.getTotalPhysicalMemory() * minFreeMemoryPercentage)));
    }

    public void eventuallyExpireDaemons(DaemonForkOptions requiredForkOptions, List<WorkerDaemonClient> idleClients, List<WorkerDaemonClient> allClients) {
        long requiredMaxHeapSize = maximumHeapHelper.getMaximumHeapSize(requiredForkOptions.getMaxHeapSize());
        long anticipatedFreeMemory = memoryInfo.getFreePhysicalMemory() - requiredMaxHeapSize;
        if (anticipatedFreeMemory < memoryThresholdInBytes) {
            if (expireDuplicateCompatibles(idleClients, allClients)) {
                anticipatedFreeMemory = memoryInfo.getFreePhysicalMemory() - requiredMaxHeapSize;
            }
            if (anticipatedFreeMemory < memoryThresholdInBytes) {
                expireLeastRecentlyUsedUntilEnoughFreeMemory(idleClients, allClients, anticipatedFreeMemory);
            }
        }
    }

    private boolean expireDuplicateCompatibles(List<WorkerDaemonClient> idleClients, List<WorkerDaemonClient> allClients) {
        boolean expired = false;
        ListIterator<WorkerDaemonClient> it = idleClients.listIterator(idleClients.size());
        List<WorkerDaemonClient> compatibilityUniques = Lists.newArrayListWithCapacity(idleClients.size());
        while (it.hasPrevious()) {
            final WorkerDaemonClient client = it.previous();
            boolean already = Iterables.any(compatibilityUniques, new Predicate<WorkerDaemonClient>() {
                @Override
                public boolean apply(WorkerDaemonClient candidate) {
                    return candidate.isCompatibleWith(client.getForkOptions());
                }
            });
            if (already) {
                allClients.remove(client);
                it.remove();
                client.stop();
                expired = true;
            } else {
                compatibilityUniques.add(client);
            }
        }
        return expired;
    }

    private void expireLeastRecentlyUsedUntilEnoughFreeMemory(List<WorkerDaemonClient> idleClients, List<WorkerDaemonClient> allClients, long anticipatedFreeMemory) {
        Iterator<WorkerDaemonClient> it = idleClients.iterator();
        while (it.hasNext()) {
            WorkerDaemonClient client = it.next();
            allClients.remove(client);
            it.remove();
            client.stop();
            anticipatedFreeMemory += maximumHeapHelper.getMaximumHeapSize(client.getForkOptions().getMaxHeapSize());
            if (anticipatedFreeMemory >= memoryThresholdInBytes) {
                break;
            }
        }
    }

}
