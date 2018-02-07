/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author mrieser / SBB
 */
public class RaptorRoute {

    final Facility<?> fromFacility;
    final Facility<?> toFacility;
    final double totalCosts;
    private List<RoutePart> editableParts = new ArrayList<>();
    final List<RoutePart> parts = Collections.unmodifiableList(this.editableParts);

    public RaptorRoute(Facility<?> fromFacility, Facility<?> toFacility, double totalCosts) {
        this.fromFacility = fromFacility;
        this.toFacility = toFacility;
        this.totalCosts = totalCosts;
    }

    public void addNonPt(TransitStopFacility fromStop, TransitStopFacility toStop, double depTime, double travelTime, String mode) {
        this.editableParts.add(new RoutePart(fromStop, toStop, mode, depTime, travelTime, null, null));
    }

    public void addPt(TransitStopFacility fromStop, TransitStopFacility toStop, TransitLine line, TransitRoute route, double depTime, double travelTime) {
        this.editableParts.add(new RoutePart(fromStop, toStop, TransportMode.pt, depTime, travelTime, line, route));
    }

    static final class RoutePart {
        final TransitStopFacility fromStop;
        final TransitStopFacility toStop;
        final String mode;
        final double depTime;
        final double travelTime;
        final TransitLine line;
        final TransitRoute route;

        RoutePart(TransitStopFacility fromStop, TransitStopFacility toStop, String mode, double depTime, double travelTime, TransitLine line, TransitRoute route) {
            this.fromStop = fromStop;
            this.toStop = toStop;
            this.mode = mode;
            this.depTime = depTime;
            this.travelTime = travelTime;
            this.line = line;
            this.route = route;
        }
    }
}
