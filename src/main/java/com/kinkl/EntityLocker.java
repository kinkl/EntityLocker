package com.kinkl;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EntityLocker<T> implements IEntityLocker<T> {

    private final ConcurrentMap<T, ReentrantLock> entityLocks;

    public EntityLocker() {
        this.entityLocks = new ConcurrentHashMap<>();
    }

    @Override
    public void lock(T entityId) {
        Objects.requireNonNull(entityId);
        Lock lock = this.entityLocks.get(entityId);
        if (lock == null) {
            ReentrantLock newLock = new ReentrantLock();
            lock = this.entityLocks.putIfAbsent(entityId, newLock);
            if (lock == null) {
                lock = newLock;
            }
        }
        lock.lock();
    }

    @Override
    public void unlock(T entityId) {
        Objects.requireNonNull(entityId);
        ReentrantLock lock = this.entityLocks.get(entityId);
        if (lock == null) {
            throw new IllegalStateException(String.format("There is no associated locks for entity with id %s", entityId.toString()));
        }
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        } else {
            throw new IllegalStateException(String.format("The lock of entity with id %s is held by another thread", entityId.toString()));
        }
    }

    @Override
    public boolean isLockedByAnotherThread(T entityId) {
        Objects.requireNonNull(entityId);
        ReentrantLock lock = this.entityLocks.get(entityId);
        return lock != null && !lock.isHeldByCurrentThread();
    }
}
