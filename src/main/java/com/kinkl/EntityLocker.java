package com.kinkl;

import com.kinkl.exception.DeadlockThreatException;
import com.kinkl.exception.MissingEntityLockException;
import com.kinkl.exception.OtherThreadEntityUnlockAttemptException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class EntityLocker<T> implements IEntityLocker<T> {

    private final ConcurrentMap<T, ReentrantLock> entityLocks = new ConcurrentHashMap<>();

    private final Object threadLockMapLock = new Object();

    private final Map<T, Thread> lockedEntityToThreadMap = new HashMap<>();

    private final Map<Thread, T> threadToPendingEntityLockMap = new HashMap<>();

    @Override
    public void lock(T entityId) {
        ReentrantLock lock = checkLockExists(entityId);
        if (!lock.isHeldByCurrentThread()) {
            synchronized (this.threadLockMapLock) {
                checkLockDoesNotCauseDeadlock(entityId);
                this.threadToPendingEntityLockMap.putIfAbsent(Thread.currentThread(), entityId);
            }
        }
        lock.lock();
        synchronized (this.threadLockMapLock) {
            this.threadToPendingEntityLockMap.remove(Thread.currentThread());
            this.lockedEntityToThreadMap.putIfAbsent(entityId, Thread.currentThread());
        }
    }

    @Override
    public boolean tryLock(T entityId, long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit);
        ReentrantLock lock = checkLockExists(entityId);
        if (!lock.isHeldByCurrentThread()) {
            synchronized (this.threadLockMapLock) {
                checkLockDoesNotCauseDeadlock(entityId);
                this.threadToPendingEntityLockMap.putIfAbsent(Thread.currentThread(), entityId);
            }
        }
        boolean isAcquired = lock.tryLock(timeout, unit);
        synchronized (this.threadLockMapLock) {
            this.threadToPendingEntityLockMap.remove(Thread.currentThread());
            if (isAcquired) {
                this.lockedEntityToThreadMap.putIfAbsent(entityId, Thread.currentThread());
            }
        }
        return isAcquired;
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

    private ReentrantLock checkLockExists(T entityId) {
        Objects.requireNonNull(entityId);
        ReentrantLock lock = this.entityLocks.get(entityId);
        if (lock == null) {
            ReentrantLock newLock = new ReentrantLock();
            lock = this.entityLocks.putIfAbsent(entityId, newLock);
            if (lock == null) {
                lock = newLock;
            }
        }
        return lock;
    }

    @Override
    public void unlock(T entityId) {
        Objects.requireNonNull(entityId);
        ReentrantLock lock = this.entityLocks.get(entityId);
        if (lock == null) {
            throw new MissingEntityLockException(String.format("There is no associated locks for entity with id %s", entityId.toString()));
        }
        if (lock.isHeldByCurrentThread()) {
            if (lock.getHoldCount() == 1) {
                synchronized (this.threadLockMapLock) {
                    this.lockedEntityToThreadMap.remove(entityId);
                }
            }
            lock.unlock();
        } else {
            throw new OtherThreadEntityUnlockAttemptException(String.format("The lock of entity with id %s is held by another thread", entityId.toString()));
        }
    }

    @Override
    public boolean isLockedByAnotherThread(T entityId) {
        Objects.requireNonNull(entityId);
        ReentrantLock lock = this.entityLocks.get(entityId);
        return lock != null && !lock.isHeldByCurrentThread();
    }
}
