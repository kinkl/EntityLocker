package com.kinkl;

import com.kinkl.exception.MissingEntityLockException;
import com.kinkl.exception.OtherThreadEntityUnlockAttemptException;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class EntityLocker<T> implements IEntityLocker<T> {

    private final ConcurrentMap<T, ReentrantLock> entityLocks = new ConcurrentHashMap<>();

    private final DeadlockDetector<T> deadlockDetector = new DeadlockDetector<>();

    @Override
    public void lock(T entityId) {
        ReentrantLock lock = checkLockExists(entityId);
        if (!lock.isHeldByCurrentThread()) {
            this.deadlockDetector.beforeLock(entityId);
        }
        lock.lock();
        this.deadlockDetector.afterLock(entityId, true);
    }

    @Override
    public boolean tryLock(T entityId, long timeout, TimeUnit unit) throws InterruptedException {
        ReentrantLock lock = checkLockExists(entityId);
        if (!lock.isHeldByCurrentThread()) {
            this.deadlockDetector.beforeLock(entityId);
        }
        boolean isAcquired = lock.tryLock(timeout, unit);
        this.deadlockDetector.afterLock(entityId, isAcquired);
        return isAcquired;
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
        if (!lock.isHeldByCurrentThread()) {
            throw new OtherThreadEntityUnlockAttemptException(String.format("The lock of entity with id %s is held by another thread", entityId.toString()));
        }
        if (lock.getHoldCount() == 1) {
            this.deadlockDetector.beforeUnlock(entityId);
        }
        lock.unlock();
    }

    @Override
    public boolean isLockedByAnotherThread(T entityId) {
        Objects.requireNonNull(entityId);
        ReentrantLock lock = this.entityLocks.get(entityId);
        return lock != null && !lock.isHeldByCurrentThread();
    }
}
