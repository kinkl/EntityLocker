package com.kinkl;

import java.util.concurrent.TimeUnit;

/**
 * <p>Utility class that provides synchronization mechanism similar to row-level DB locking. The class is supposed to be used by the components
 * that are responsible for managing storage and caching of different type of entities in the application. It does not deal with the entities,
 * only with the IDs (primary keys) of the entities.</p>
 * Main features are:
 * <ul>
 * <li>Support different entity ID types</li>
 * <li>The caller is able to specify which entity it wants to work with (using entity ID), and designate the boundaries of the code that should
 * have exclusive access to the entity (called “protected code”)</li>
 * <li>For any given entity, is's guaranteed that at most one thread executes protected code on that entity. If there’s a concurrent request to
 * lock the same entity, the other thread should wait until the entity becomes available</li>
 * <li>Support of concurrent execution of protected code on different entities</li>
 * <li>Support of reentrant locking</li>
 * <li>Support of timeout for entity locking</li>
 * <li>Protection from deadlocks</li>
 * </ul>
 * @param <T> the type of element id
 */
public interface IEntityLocker<T> {
    /**
     * Locks entity if passed id is not null and this will not cause deadlock. See {@link java.util.concurrent.locks.ReentrantLock#lock} for details.
     * @param entityId id of entity to lock
     * @throws com.kinkl.exception.DeadlockThreatException if lock causes deadlock
     * @throws NullPointerException if entityId is null
     */
    void lock(T entityId);

    /**
     * Unlocks entity if passed id is not null and this entity has an associated lock and this entity is not locked by another thread.
     * See {@link java.util.concurrent.locks.ReentrantLock#unlock} for details.
     * @param entityId id of entity to lock
     * @throws com.kinkl.exception.MissingEntityLockException if entity doesn't have associated locks
     * @throws com.kinkl.exception.OtherThreadEntityUnlockAttemptException if entity is locked by another thread
     * @throws NullPointerException if entityId is null
     */
    void unlock(T entityId);

    /**
     * Returns true if entity is locked by another thread. Otherwise false.
     * @param entityId id of entity to lock
     * @return true if entity is locked by another thread. Otherwise false
     * @throws NullPointerException if entityId is null
     */
    boolean isLockedByAnotherThread(T entityId);

    /**
     * Locks entity if passed id is not null, this will not cause deadlock, it is not held by another thread within the given waiting time
     * and the current thread has not been interrupted. See {@link java.util.concurrent.locks.ReentrantLock#tryLock} for details.
     * @param entityId id of entity to lock
     * @param timeout the time to wait for the lock
     * @param unit the time unit of the timeout argument
     * @return true if entity was locked successfully. Otherwise false
     * @throws InterruptedException if the current thread is interrupted
     * @throws com.kinkl.exception.DeadlockThreatException if lock causes deadlock
     * @throws NullPointerException if entityId is null
     */
    boolean tryLock(T entityId, long timeout, TimeUnit unit) throws InterruptedException;
}
