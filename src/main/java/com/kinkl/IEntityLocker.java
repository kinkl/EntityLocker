package com.kinkl;

import java.util.concurrent.TimeUnit;

/**
 * TODO add javadoc
 * @param <T>
 */
public interface IEntityLocker<T> {
    // TODO test if thread can lock and unlock entity
    // TODO test reentrant lock and unlock
    /**
     * TODO add javadoc
     * @param entityId
     */
    void lock(T entityId);
    // TODO test method throws runtime exception if thread doesn't own the lock
    /**
     * TODO add javadoc
     * @param entityId
     */
    void unlock(T entityId);
    // TODO test method throws runtime exception if thread doesn't own the lock
    /**
     * TODO add javadoc This method is designed for test purposes mainly but it can also be useful for client classes
     * @param entityId
     */
    boolean isLockedByAnotherThread(T entityId);

    /**
     * TODO add javadoc
     * @param entityId
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    boolean tryLock(T entityId, long timeout, TimeUnit unit) throws InterruptedException;
}
