package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsessionapi.model.Investigation;
import org.bukkit.Location;

public final class DefaultAreaBoundaryProvider implements AreaBoundaryProvider {
    private static final double DEFAULT_RADIUS = 24.0D;

    /**
     * Temporary default boundary provider used until a future phase supplies a
     * real area selection source.
     */
    public Investigation withDefaultBounds(Investigation base, Location anchor) {
        if (base == null || anchor == null || anchor.getWorld() == null) {
            return base;
        }
        return new Investigation(
            base.getInvestigationId(),
            base.getStaffer(),
            base.getTarget(),
            base.getType(),
            base.getStatus(),
            base.getSourceReportId(),
            base.getStartedAt(),
            base.getEndedAt(),
            anchor.getWorld().getName(),
            anchor.getX() - DEFAULT_RADIUS,
            anchor.getY() - DEFAULT_RADIUS,
            anchor.getZ() - DEFAULT_RADIUS,
            anchor.getX() + DEFAULT_RADIUS,
            anchor.getY() + DEFAULT_RADIUS,
            anchor.getZ() + DEFAULT_RADIUS
        );
    }

    @Override
    public Investigation createBoundarySnapshot(Investigation base, Location anchor) {
        return withDefaultBounds(base, anchor);
    }
}
