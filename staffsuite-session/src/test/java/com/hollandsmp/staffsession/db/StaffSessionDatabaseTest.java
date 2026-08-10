package com.hollandsmp.staffsession.db;

import com.hollandsmp.staffsessionapi.model.FailureReason;
import com.hollandsmp.staffsessionapi.model.InvestigationResult;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import com.hollandsmp.staffsession.core.StateMachine;
import com.hollandsmp.staffsession.integrity.CorruptedProtection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
        StaffSessionDatabase bootstrap = new StaffSessionDatabase(dbFile);
        bootstrap.initialize();
        bootstrap.close();
        final UUID staffer = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        Future<SQLiteOutcome> a = executor.submit(createRawInsertTask(staffer, UUID.randomUUID(), gate));
        Future<SQLiteOutcome> b = executor.submit(createRawInsertTask(staffer, UUID.randomUUID(), gate));
        gate.countDown();

        int successCount = 0;
        int failureCount = 0;
        if (a.get(10, TimeUnit.SECONDS).success) successCount++; else failureCount++;
        if (b.get(10, TimeUnit.SECONDS).success) successCount++; else failureCount++;

        Assert.assertEquals(1, successCount);
        Assert.assertEquals(1, failureCount);
        executor.shutdownNow();
    }

    @Test
    public void concurrentStartsForSameTargetYieldSingleActiveRow() throws Exception {
        StaffSessionDatabase bootstrap = new StaffSessionDatabase(dbFile);
        bootstrap.initialize();
        bootstrap.close();
        final UUID target = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        Future<SQLiteOutcome> a = executor.submit(createRawInsertTask(UUID.randomUUID(), target, gate));
        Future<SQLiteOutcome> b = executor.submit(createRawInsertTask(UUID.randomUUID(), target, gate));
        gate.countDown();

        int successCount = 0;
        int failureCount = 0;
        if (a.get(10, TimeUnit.SECONDS).success) successCount++; else failureCount++;
        if (b.get(10, TimeUnit.SECONDS).success) successCount++; else failureCount++;

        Assert.assertEquals(1, successCount);
        Assert.assertEquals(1, failureCount);
        executor.shutdownNow();
    }

    @Test
    public void recoveryConvertsActiveInvestigationsToCrashRecovered() throws Exception {
        StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
        database.initialize();
        UUID staffer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        InvestigationResult start = database.startInvestigation(staffer, target, InvestigationType.PLAYER, "r1");
        Assert.assertTrue(start.isSuccessful());
        database.close();

        StaffSessionDatabase reopened = new StaffSessionDatabase(dbFile);
        reopened.initialize();
        reopened.recoverActiveInvestigations();
        Assert.assertFalse(reopened.isStafferInSession(staffer));
        Assert.assertEquals(InvestigationStatus.CRASHED_RECOVERED, reopened.findInvestigationById(start.getInvestigation().getInvestigationId()).get().getStatus());
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

    @Test
    public void stateMachineRejectsInvalidTransitions() {
        StateMachine stateMachine = new StateMachine();
        Assert.assertTrue(stateMachine.canTransition(StateMachine.State.ACTIVE, StateMachine.State.ENDED));
        Assert.assertTrue(stateMachine.canTransition(StateMachine.State.ACTIVE, StateMachine.State.CRASHED_RECOVERED));
        Assert.assertTrue(stateMachine.canTransition(StateMachine.State.ACTIVE, StateMachine.State.CORRUPTED));
        Assert.assertFalse(stateMachine.canTransition(StateMachine.State.ENDED, StateMachine.State.ACTIVE));
        Assert.assertFalse(stateMachine.canTransition(StateMachine.State.CORRUPTED, StateMachine.State.ACTIVE));
    }

    @Test
    public void corruptedProtectionDetectsInvalidPlayerInvestigation() {
        CorruptedProtection protection = new CorruptedProtection();
        com.hollandsmp.staffsessionapi.model.Investigation investigation =
            new com.hollandsmp.staffsessionapi.model.Investigation(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                null,
                InvestigationType.PLAYER,
                InvestigationStatus.ACTIVE,
                null,
                System.currentTimeMillis(),
                null
            );
        Assert.assertTrue(protection.isCorrupted(investigation));
    }

    @Test
    public void malformedRowIsRecoveredAsCorrupted() throws Exception {
        StaffSessionDatabase database = new StaffSessionDatabase(dbFile);
        database.initialize();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             PreparedStatement ps = connection.prepareStatement("INSERT INTO investigations(investigation_id, staffer, target, type, status, source_report_id, started_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, "not-a-uuid");
            ps.setNull(3, java.sql.Types.VARCHAR);
            ps.setString(4, InvestigationType.PLAYER.name());
            ps.setString(5, InvestigationStatus.ACTIVE.name());
            ps.setString(6, "bad");
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        }
        database.close();

        StaffSessionDatabase reopened = new StaffSessionDatabase(dbFile);
        reopened.initialize();
        reopened.recoverActiveInvestigations();
        Assert.assertTrue(reopened.loadActiveInvestigations().isEmpty());
        reopened.close();
    }

    private Callable<SQLiteOutcome> createRawInsertTask(final UUID staffer, final UUID target, final CountDownLatch startGate) {
        return new Callable<SQLiteOutcome>() {
            @Override
            public SQLiteOutcome call() throws Exception {
                startGate.await(10, TimeUnit.SECONDS);
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                     PreparedStatement ps = connection.prepareStatement("INSERT INTO investigations(investigation_id, staffer, target, type, status, source_report_id, started_at) VALUES(?,?,?,?,?,?,?)")) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, staffer.toString());
                    ps.setString(3, target.toString());
                    ps.setString(4, InvestigationType.PLAYER.name());
                    ps.setString(5, InvestigationStatus.ACTIVE.name());
                    ps.setString(6, "r");
                    ps.setLong(7, System.currentTimeMillis());
                    ps.executeUpdate();
                    return new SQLiteOutcome(true);
                } catch (SQLException e) {
                    return new SQLiteOutcome(false);
                }
            }
        };
    }

    private static final class SQLiteOutcome {
        private final boolean success;

        private SQLiteOutcome(boolean success) {
            this.success = success;
        }
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
