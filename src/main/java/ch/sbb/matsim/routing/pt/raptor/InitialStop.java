/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * Specifies the access or egress time and costs for a specific TransitStopFacility and a specific mode.
 *
 * @author mrieser / SBB
 */
public class InitialStop {

    final TransitStopFacility stop;
    final double accessCost;
    final double accessTime;
    final String mode;

    public InitialStop(TransitStopFacility stop, double accessCost, double accessTime, String mode) {
        this.stop = stop;
        this.accessCost = accessCost;
        this.accessTime = accessTime;
        this.mode = mode;
    }
}
