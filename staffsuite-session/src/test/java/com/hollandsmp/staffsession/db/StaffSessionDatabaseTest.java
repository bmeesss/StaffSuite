package com.hollandsmp.staffsession.db;

import com.hollandsmp.staffsessionapi.model.FailureReason;
import com.hollandsmp.staffsessionapi.model.InvestigationResult;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class StaffSessionDatabaseTest {
    private File tempDir;
    private File dbFile;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("staffsession-test").toFile();
        dbFile = new File(tempDir, "staffsession.db");
    }

    @After
    public void tearDown() {
        deleteRecursively(tempDir);
    }

    @Test
    public void startAndEndLifecycle() {
        StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
        database.initialize();
        UUID staffer = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        InvestigationResult start = database.startInvestigation(staffer, target, InvestigationType.PLAYER, "r1");
        Assert.assertTrue(start.isSuccessful());
        Assert.assertTrue(database.isStafferInSession(staffer));
        Assert.assertTrue(database.isPlayerBeingInvestigated(target));

        InvestigationResult end = database.endInvestigation(staffer);
        Assert.assertTrue(end.isSuccessful());
        Assert.assertFalse(database.isStafferInSession(staffer));
        Assert.assertFalse(database.isPlayerBeingInvestigated(target));
        Optional<com.hollandsmp.staffsessionapi.model.Investigation> active = database.getActiveInvestigation(staffer);
        Assert.assertFalse(active.isPresent());
        database.close();
    }

    @Test
    public void concurrentStartsForSameStafferYieldSingleActiveRow() throws Exception {
        final UUID staffer = UUID.randomUUID();
        final CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<InvestigationResult> first = createStartTask(staffer, UUID.randomUUID(), startGate);
        Callable<InvestigationResult> second = createStartTask(staffer, UUID.randomUUID(), startGate);

        Future<InvestigationResult> a = executor.submit(first);
        Future<InvestigationResult> b = executor.submit(second);
        startGate.countDown();

        int successCount = 0;
        int failureCount = 0;
        if (a.get(10, TimeUnit.SECONDS).isSuccessful()) successCount++; else failureCount++;
        if (b.get(10, TimeUnit.SECONDS).isSuccessful()) successCount++; else failureCount++;

        Assert.assertEquals(1, successCount);
        Assert.assertEquals(1, failureCount);

        StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
        database.initialize();
        Assert.assertTrue(database.isStafferInSession(staffer));
        database.close();
        executor.shutdownNow();
    }

    @Test
    public void concurrentStartsForSameTargetYieldSingleActiveRow() throws Exception {
        final UUID target = UUID.randomUUID();
        final CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<InvestigationResult> first = createStartTask(UUID.randomUUID(), target, startGate);
        Callable<InvestigationResult> second = createStartTask(UUID.randomUUID(), target, startGate);

        Future<InvestigationResult> a = executor.submit(first);
        Future<InvestigationResult> b = executor.submit(second);
        startGate.countDown();

        int successCount = 0;
        int failureCount = 0;
        if (a.get(10, TimeUnit.SECONDS).isSuccessful()) successCount++; else failureCount++;
        if (b.get(10, TimeUnit.SECONDS).isSuccessful()) successCount++; else failureCount++;

        Assert.assertEquals(1, successCount);
        Assert.assertEquals(1, failureCount);

        StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
        database.initialize();
        Assert.assertTrue(database.isPlayerBeingInvestigated(target));
        database.close();
        executor.shutdownNow();
    }

    @Test
    public void recoveryConvertsActiveInvestigationsToCrashRecovered() throws Exception {
        StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
        database.initialize();
        UUID staffer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Assert.assertTrue(database.startInvestigation(staffer, target, InvestigationType.PLAYER, "r1").isSuccessful());
        database.close();

        StaffSessionDatabase reopened = new StaffSessionDatabase(dbFile);
        reopened.initialize();
        reopened.recoverActiveInvestigations();
        Assert.assertFalse(reopened.isStafferInSession(staffer));
        reopened.close();
    }

    @Test
    public void endingWithoutActiveSessionFailsCleanly() {
        StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
        database.initialize();
        InvestigationResult result = database.endInvestigation(UUID.randomUUID());
        Assert.assertFalse(result.isSuccessful());
        Assert.assertEquals(FailureReason.NO_ACTIVE_SESSION, result.getFailureReason());
        database.close();
    }

    private Callable<InvestigationResult> createStartTask(final UUID staffer, final UUID target, final CountDownLatch startGate) {
        return new Callable<InvestigationResult>() {
            @Override
            public InvestigationResult call() throws Exception {
                startGate.await(10, TimeUnit.SECONDS);
                StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
                database.initialize();
                try {
                    return database.startInvestigation(staffer, target, InvestigationType.PLAYER, "r");
                } finally {
                    database.close();
                }
            }
        };
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
