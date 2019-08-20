package com.kinkl;

import com.kinkl.exception.DeadlockThreatException;

import java.util.HashMap;
import java.util.Map;

public class DeadlockDetector<T> {

    private final Map<T, Thread> lockedEntityToThreadMap = new HashMap<>();

    private final Map<Thread, T> threadToPendingEntityLockMap = new HashMap<>();

    synchronized void beforeLock(T entityId) {
        checkLockDoesNotCauseDeadlock(entityId);
        this.threadToPendingEntityLockMap.putIfAbsent(Thread.currentThread(), entityId);
    }

    synchronized void afterLock(T entityId, boolean isLockAcquired) {
        this.threadToPendingEntityLockMap.remove(Thread.currentThread());
        if (isLockAcquired) {
            this.lockedEntityToThreadMap.putIfAbsent(entityId, Thread.currentThread());
        }
    }

    synchronized void beforeUnlock(T entityId) {
        this.lockedEntityToThreadMap.remove(entityId);
    }

    private void checkLockDoesNotCauseDeadlock(T entityId) {
        T nextEntityId = entityId;
        while (nextEntityId != null) {
            Thread thread = this.lockedEntityToThreadMap.get(nextEntityId);
            if (thread == Thread.currentThread()) {
                String msg = String.format("Thread [%s] cannot lock entity with id %s because this will cause a deadlock. This entity is already locked by thread [%s]",
                        Thread.currentThread().getName(),
                        entityId,
                        this.lockedEntityToThreadMap.get(entityId).getName());
                throw new DeadlockThreatException(msg);
            }
            nextEntityId = this.threadToPendingEntityLockMap.get(thread);
        }
    }
}
