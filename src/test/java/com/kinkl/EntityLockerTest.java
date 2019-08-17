package com.kinkl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class EntityLockerTest {

    @Rule
    public ExpectedException expectedRule = ExpectedException.none();
    private IEntityLocker<Integer> entityLocker;

    @Before
    public void setUp() {
        this.entityLocker = new EntityLocker<Integer>();
    }

    @Test
    public void testLockThrowsExceptionWhenArgumentIsNull() {
        this.expectedRule.expect(NullPointerException.class);
        this.entityLocker.lock(null);
    }

    @Test
    public void testUnlockThrowsExceptionWhenArgumentIsNull() {
        this.expectedRule.expect(NullPointerException.class);
        this.entityLocker.unlock(null);
    }

    @Test
    public void testIsLockedByAnotherThreadThrowsExceptionWhenArgumentIsNull() {
        this.expectedRule.expect(NullPointerException.class);
        this.entityLocker.isLockedByAnotherThread(null);
    }

    @Test
    public void testUnlockThrowsExceptionWhenEntityWasNotLocked() {
        this.expectedRule.expect(IllegalStateException.class);
        this.expectedRule.expectMessage("There is no associated locks for entity with id 123");
        this.entityLocker.unlock(123);
    }

    @Test
    public void testUnlockThrowsExceptionWhenEntityIsLockedByAnotherThread() {
        this.expectedRule.expect(IllegalStateException.class);
        this.expectedRule.expectMessage("The lock of entity with id 123 is held by another thread");
        CountDownLatch subThreadLockedEntityLatch = new CountDownLatch(1);
        CountDownLatch subThreadIsAllowedToUnlockEntityLatch = new CountDownLatch(1);
        Thread subThread = new Thread(() -> {
            this.entityLocker.lock(123);
            subThreadLockedEntityLatch.countDown();
            try {
                subThreadIsAllowedToUnlockEntityLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail();
            }
            this.entityLocker.unlock(123);
        });
        subThread.start();

        try {
            subThreadLockedEntityLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail();
        }
        assertTrue(this.entityLocker.isLockedByAnotherThread(123));
        try {
            this.entityLocker.unlock(123);
        } finally {
            subThreadIsAllowedToUnlockEntityLatch.countDown();
        }
    }

    @Test
    public void testLockAndUnlockWorkAsDesigned() {
        SimpleEntity entity = new SimpleEntity(123, "A");

        // Here we guarantee that the subthread will get entity lock before the main thread so the latest changes will be done by main thread
        CountDownLatch subThreadIsAboutToUnlockEntityLatch = new CountDownLatch(1);
        CountDownLatch subThreadLockedEntityLatch = new CountDownLatch(1);
        Thread subThread = new Thread(() -> {
            this.entityLocker.lock(entity.getId());
            subThreadLockedEntityLatch.countDown();
            entity.setValue("B");
            try {
                subThreadIsAboutToUnlockEntityLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail();
            }
            this.entityLocker.unlock(entity.getId());
        });
        assertEquals("A", entity.getValue());
        subThread.start();

        try {
            subThreadLockedEntityLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail();
        }

        assertTrue(this.entityLocker.isLockedByAnotherThread(entity.getId()));
        subThreadIsAboutToUnlockEntityLatch.countDown();

        this.entityLocker.lock(entity.getId());
        assertEquals("B", entity.getValue());
        entity.setValue("C");
        this.entityLocker.unlock(entity.getId());
        assertEquals("C", entity.getValue());

        // Now vice versa - make the main thread retrieve entity lock before the subthread. Here the latest changes are done by the subthread
        CountDownLatch mainIsAboutToUnlockEntityLatch = new CountDownLatch(1);
        CountDownLatch mainLockedEntityLatch = new CountDownLatch(1);
        CountDownLatch subThreadIsFinishedLatch = new CountDownLatch(1);
        subThread = new Thread(() -> {
            try {
                mainLockedEntityLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail();
            }
            this.entityLocker.isLockedByAnotherThread(entity.getId());
            mainIsAboutToUnlockEntityLatch.countDown();

            this.entityLocker.lock(entity.getId());
            assertEquals("E", entity.getValue());
            subThreadLockedEntityLatch.countDown();
            entity.setValue("D");
            this.entityLocker.unlock(entity.getId());
            subThreadIsFinishedLatch.countDown();
        });
        subThread.start();

        this.entityLocker.lock(entity.getId());
        mainLockedEntityLatch.countDown();
        entity.setValue("E");
        try {
            mainIsAboutToUnlockEntityLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail();
        }
        this.entityLocker.unlock(entity.getId());

        try {
            subThreadIsFinishedLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail();
        }
        // Call lock and unlock to guarantee visibility of changes (happens-before)
        this.entityLocker.lock(entity.getId());
        this.entityLocker.unlock(entity.getId());
        assertEquals("D", entity.getValue());
    }

    private static class SimpleEntity {

        private final int id;

        private String value;

        public SimpleEntity(int id, String value) {
            this.id = id;
            this.value = value;
        }

        public int getId() {
            return this.id;
        }

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}