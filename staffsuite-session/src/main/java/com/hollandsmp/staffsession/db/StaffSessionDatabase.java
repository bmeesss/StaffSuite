package com.hollandsmp.staffsession.db;

import com.hollandsmp.staffsessionapi.model.FailureReason;
import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationResult;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import com.hollandsmp.staffsession.integrity.CorruptedProtection;
import com.hollandsmp.staffsession.integrity.SecurityAudit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StaffSessionDatabase {
    private final JavaPlugin plugin;
    private final File explicitDatabaseFile;
    private Connection connection;
    private boolean available;
    private final CorruptedProtection corruptedProtection = new CorruptedProtection();

    public StaffSessionDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.explicitDatabaseFile = null;
    }

    public StaffSessionDatabase(File explicitDatabaseFile) {
        this.plugin = null;
        this.explicitDatabaseFile = explicitDatabaseFile;
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = explicitDatabaseFile != null ? explicitDatabaseFile : new File(plugin.getDataFolder(), "staffsession.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS investigations (" +
                        "investigation_id TEXT PRIMARY KEY," +
                        "staffer TEXT NOT NULL," +
                        "target TEXT," +
                        "type TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "source_report_id TEXT," +
                        "started_at INTEGER NOT NULL," +
                        "ended_at INTEGER)");
                statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_per_staffer ON investigations(staffer) WHERE status = 'ACTIVE'");
                statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_target ON investigations(target) WHERE status = 'ACTIVE' AND type = 'PLAYER'");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS security_audit (" +
                        "audit_id TEXT PRIMARY KEY," +
                        "investigation_id TEXT," +
                        "staffer TEXT," +
                        "target TEXT," +
                        "event_type TEXT NOT NULL," +
                        "details TEXT," +
                        "created_at INTEGER NOT NULL)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_security_audit_investigation ON security_audit(investigation_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_security_audit_created_at ON security_audit(created_at)");
            }
            available = true;
        } catch (Exception e) {
            available = false;
            throw new IllegalStateException("Unable to initialize StaffSession database", e);
        }
    }

    public boolean isAvailable() {
        return available && connection != null;
    }

    public synchronized InvestigationResult startInvestigation(UUID staffer, UUID target, InvestigationType type, String reportId) {
        if (!isAvailable()) return InvestigationResult.failure(FailureReason.SESSION_UNAVAILABLE);
        if (staffer == null || type == null) return InvestigationResult.failure(FailureReason.INVALID_STATE);
        if (type == InvestigationType.PLAYER && target == null) return InvestigationResult.failure(FailureReason.INVALID_TARGET);
        String investigationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO investigations(investigation_id, staffer, target, type, status, source_report_id, started_at) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, investigationId);
                ps.setString(2, staffer.toString());
                ps.setString(3, target == null ? null : target.toString());
                ps.setString(4, type.name());
                ps.setString(5, InvestigationStatus.ACTIVE.name());
                ps.setString(6, reportId);
                ps.setLong(7, now);
                ps.executeUpdate();
            }
            audit(investigationId, staffer, target, SecurityAudit.EventType.INVESTIGATION_STARTED.name(), "active");
            connection.commit();
            connection.setAutoCommit(true);
            return InvestigationResult.success(new Investigation(investigationId, staffer, target, type, InvestigationStatus.ACTIVE, reportId, now, null));
        } catch (SQLException e) {
            rollbackQuietly();
            return mapConstraintFailure(e);
        }
    }

    public synchronized InvestigationResult endInvestigation(UUID staffer) {
        Optional<Investigation> active = getActiveInvestigation(staffer);
        if (!active.isPresent()) {
            return InvestigationResult.failure(FailureReason.NO_ACTIVE_SESSION);
        }
        Investigation current = active.get();
        long now = System.currentTimeMillis();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("UPDATE investigations SET status = ?, ended_at = ? WHERE investigation_id = ?")) {
                ps.setString(1, InvestigationStatus.ENDED.name());
                ps.setLong(2, now);
                ps.setString(3, current.getInvestigationId());
                ps.executeUpdate();
            }
            audit(current.getInvestigationId(), staffer, current.getTarget(), SecurityAudit.EventType.INVESTIGATION_ENDED.name(), "ended");
            connection.commit();
            connection.setAutoCommit(true);
            return InvestigationResult.success(new Investigation(current.getInvestigationId(), current.getStaffer(), current.getTarget(), current.getType(), InvestigationStatus.ENDED, current.getSourceReportId(), current.getStartedAt(), now));
        } catch (SQLException e) {
            rollbackQuietly();
            return InvestigationResult.failure(FailureReason.DATABASE_ERROR);
        }
    }

    public synchronized boolean isStafferInSession(UUID staffer) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM investigations WHERE staffer = ? AND status = 'ACTIVE' LIMIT 1")) {
            ps.setString(1, staffer.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean isPlayerBeingInvestigated(UUID target) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM investigations WHERE target = ? AND status = 'ACTIVE' AND type = 'PLAYER' LIMIT 1")) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized Optional<Investigation> getActiveInvestigation(UUID staffer) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT investigation_id, staffer, target, type, status, source_report_id, started_at, ended_at FROM investigations WHERE staffer = ? AND status = 'ACTIVE' LIMIT 1")) {
            ps.setString(1, staffer.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                try {
                    return Optional.of(readInvestigation(rs));
                } catch (RuntimeException malformed) {
                    markCorruptedById(rs.getString("investigation_id"), "malformed row");
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public synchronized void recoverActiveInvestigations() {
        List<Investigation> activeInvestigations = loadActiveInvestigations();
        for (Investigation investigation : activeInvestigations) {
            if (corruptedProtection.isCorrupted(investigation)) {
                markCorrupted(investigation, "invalid active row");
                continue;
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE investigations SET status = ?, ended_at = ? WHERE investigation_id = ?")) {
                ps.setString(1, InvestigationStatus.CRASHED_RECOVERED.name());
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, investigation.getInvestigationId());
                ps.executeUpdate();
                audit(investigation.getInvestigationId(), investigation.getStaffer(), investigation.getTarget(), SecurityAudit.EventType.INVESTIGATION_RECOVERED.name(), "recovered");
            } catch (SQLException ignored) {
                markCorrupted(investigation, "recovery failed");
            }
        }
    }

    public synchronized List<Investigation> loadActiveInvestigations() {
        List<Investigation> result = new ArrayList<Investigation>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT investigation_id, staffer, target, type, status, source_report_id, started_at, ended_at FROM investigations WHERE status = 'ACTIVE'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String investigationId = rs.getString("investigation_id");
                    try {
                        result.add(readInvestigation(rs));
                    } catch (RuntimeException malformed) {
                        markCorruptedById(investigationId, "malformed row");
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    public synchronized Optional<Investigation> findInvestigationById(String investigationId) {
        if (investigationId == null) {
            return Optional.empty();
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT investigation_id, staffer, target, type, status, source_report_id, started_at, ended_at FROM investigations WHERE investigation_id = ? LIMIT 1")) {
            ps.setString(1, investigationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(readInvestigation(rs));
                } catch (RuntimeException malformed) {
                    markCorruptedById(investigationId, "malformed row");
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private InvestigationResult mapConstraintFailure(SQLException e) {
        String message = e.getMessage();
        if (message != null && message.contains("idx_one_active_per_staffer")) {
            return InvestigationResult.failure(FailureReason.STAFFER_ALREADY_IN_SESSION);
        }
        if (message != null && message.contains("idx_one_active_target")) {
            return InvestigationResult.failure(FailureReason.TARGET_ALREADY_INVESTIGATED);
        }
        return InvestigationResult.failure(FailureReason.DATABASE_ERROR);
    }

    private void audit(String investigationId, UUID staffer, UUID target, String eventType, String details) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO security_audit(audit_id, investigation_id, staffer, target, event_type, details, created_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, investigationId);
            ps.setString(3, staffer == null ? null : staffer.toString());
            ps.setString(4, target == null ? null : target.toString());
            ps.setString(5, eventType);
            ps.setString(6, details);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void markCorrupted(Investigation investigation, String reason) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE investigations SET status = ?, ended_at = ? WHERE investigation_id = ?")) {
            ps.setString(1, InvestigationStatus.CORRUPTED.name());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, investigation.getInvestigationId());
            ps.executeUpdate();
            audit(investigation.getInvestigationId(), investigation.getStaffer(), investigation.getTarget(), SecurityAudit.EventType.INVESTIGATION_CORRUPTED.name(), reason);
        } catch (SQLException ignored) {
        }
    }

    private void markCorruptedById(String investigationId, String reason) {
        if (investigationId == null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE investigations SET status = ?, ended_at = ? WHERE investigation_id = ?")) {
            ps.setString(1, InvestigationStatus.CORRUPTED.name());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, investigationId);
            ps.executeUpdate();
            audit(investigationId, null, null, SecurityAudit.EventType.INVESTIGATION_CORRUPTED.name(), reason);
        } catch (SQLException ignored) {
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private Investigation readInvestigation(ResultSet rs) throws SQLException {
        return new Investigation(
            rs.getString("investigation_id"),
            UUID.fromString(rs.getString("staffer")),
            rs.getString("target") == null ? null : UUID.fromString(rs.getString("target")),
            InvestigationType.valueOf(rs.getString("type")),
            InvestigationStatus.valueOf(rs.getString("status")),
            rs.getString("source_report_id"),
            rs.getLong("started_at"),
            rs.getObject("ended_at") == null ? null : rs.getLong("ended_at")
        );
    }

    public synchronized void close() {
        available = false;
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }
}
