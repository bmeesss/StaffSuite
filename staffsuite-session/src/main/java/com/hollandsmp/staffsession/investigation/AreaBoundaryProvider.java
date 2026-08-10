package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsessionapi.model.Investigation;
import org.bukkit.Location;

public interface AreaBoundaryProvider {
    Investigation createBoundarySnapshot(Investigation base, Location anchor);
}
