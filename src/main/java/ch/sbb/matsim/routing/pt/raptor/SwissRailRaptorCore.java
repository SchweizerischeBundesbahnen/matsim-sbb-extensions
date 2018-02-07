/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RRoute;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RRouteStop;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RTransfer;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The actual RAPTOR implementation, based on Delling et al, Round-Based Public Transit Routing.
 *
 * This class is <b>NOT</b> thread-safe due to the use of internal state during the route calculation.
 *
 * @author mrieser / SBB
 */
public class SwissRailRaptorCore {

    private final SwissRailRaptorData data;
    private final RaptorConfig config;

    private final PathElement[] arrivalPathPerRouteStop;
    private final double[] egressCostsPerRouteStop;
    private final double[] leastArrivalCostAtRouteStop;
    private final double[] leastArrivalCostAtStop;
    private final BitSet improvedRouteStopIndices;
    private final BitSet reachedRouteStopIndices;
    private final BitSet improvedStops;
    private final BitSet destinationRouteStopIndices;
    private double bestArrivalCost = Double.POSITIVE_INFINITY;
    private final PathElement[] arrivalPathPerStop;

    public SwissRailRaptorCore(SwissRailRaptorData data) {
        this.data = data;
        this.config = data.config;
        this.arrivalPathPerRouteStop = new PathElement[data.countRouteStops];
        this.egressCostsPerRouteStop = new double[data.countRouteStops];
        this.leastArrivalCostAtRouteStop = new double[data.countRouteStops];
        this.leastArrivalCostAtStop = new double[data.countStops];
        this.improvedRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.reachedRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.destinationRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.improvedStops = new BitSet(this.data.countStops);
        this.arrivalPathPerStop = new PathElement[this.data.countStops];
    }

    private void reset() {
        Arrays.fill(this.arrivalPathPerRouteStop, null);
        Arrays.fill(this.egressCostsPerRouteStop, Double.POSITIVE_INFINITY);
        Arrays.fill(this.arrivalPathPerStop, null);
        Arrays.fill(this.leastArrivalCostAtRouteStop, Double.POSITIVE_INFINITY);
        Arrays.fill(this.leastArrivalCostAtStop, Double.POSITIVE_INFINITY);
        this.improvedRouteStopIndices.clear();
        this.reachedRouteStopIndices.clear();
        this.destinationRouteStopIndices.clear();
        this.bestArrivalCost = Double.POSITIVE_INFINITY;
    }

    public RaptorRoute calcLeastCostRoute(double depTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        final int maxTransfers = 20; // sensible defaults, could be made configurable if there is a need for it.
        final int maxTransfersAfterFirstArrival = 2;

        reset();

        Map<TransitStopFacility, InitialStop> destinationStops = new HashMap<>();
        for (InitialStop egressStop : egressStops) {
            destinationStops.put(egressStop.stop, egressStop);
            int[] routeStopIndices = this.data.routeStopsPerStopFacility.get(egressStop.stop);
            if (routeStopIndices != null) {
                for (int routeStopIndex : routeStopIndices) {
                    this.destinationRouteStopIndices.set(routeStopIndex);
                    this.egressCostsPerRouteStop[routeStopIndex] = egressStop.accessCost;
                }
            }
        }

        for (InitialStop stop : accessStops) {
            int[] routeStopIndices = this.data.routeStopsPerStopFacility.get(stop.stop);
            for (int routeStopIndex : routeStopIndices) {
                double arrivalTime = depTime + stop.accessTime;
                double arrivalCost = stop.accessCost;
                RRouteStop toRouteStop = this.data.routeStops[routeStopIndex];
                PathElement pe = new PathElement(null, toRouteStop, arrivalTime, arrivalCost, 0, true);
                this.arrivalPathPerRouteStop[routeStopIndex] = pe;
                this.arrivalPathPerStop[toRouteStop.stopFacilityIndex] = pe;
                this.leastArrivalCostAtRouteStop[routeStopIndex] = arrivalCost;
                this.leastArrivalCostAtStop[toRouteStop.stopFacilityIndex] = arrivalCost;
                this.improvedRouteStopIndices.set(routeStopIndex);
            }
        }

        int allowedTransfersLeft = maxTransfersAfterFirstArrival;
        // the main loop
        for (int k = 0; k <= maxTransfers; k++) {
            // first stage (according to paper) is to set earliestArrivalTime_k(stop) = earliestArrivalTime_k-1(stop)
            // but because we re-use the earliestArrivalTime-array, we don't have to do anything.

            // second stage: process routes
            exploreRoutes();

            PathElement leastCostPath = findLeastCostArrival(destinationStops);
            if (leastCostPath != null) {
                if (allowedTransfersLeft == 0) {
                    break;
                }
                allowedTransfersLeft--;
            }

            if (this.improvedStops.isEmpty()) {
                break;
            }

            // third stage (according to paper): handle footpaths / transfers
            handleTransfers();

            // final stage: check stop criterion
            if (this.improvedRouteStopIndices.isEmpty()) {
                break;
            }
        }

        // create RaptorRoute based on PathElements
        PathElement leastCostPath = findLeastCostArrival(destinationStops);
        RaptorRoute raptorRoute = createRaptorRoute(leastCostPath, depTime);
        return raptorRoute;
    }

    private void exploreRoutes() {
        this.improvedStops.clear();
        this.reachedRouteStopIndices.clear();

        int routeIndex = -1;
        for (int firstRouteStopIndex = this.improvedRouteStopIndices.nextSetBit(0); firstRouteStopIndex >= 0; firstRouteStopIndex = this.improvedRouteStopIndices.nextSetBit(firstRouteStopIndex+1)) {
            RRouteStop firstRouteStop = this.data.routeStops[firstRouteStopIndex];
            if (firstRouteStop.transitRouteIndex == routeIndex) {
                continue; // we've handled this route already
            }
            int tmpRouteIndex = firstRouteStop.transitRouteIndex;

            // for each relevant route, step along route and look for new/improved connections
            RRoute route = this.data.routes[tmpRouteIndex];

            // firstRouteStop is the first RouteStop in the route we can board in this round
            // figure out which departure we can take
            PathElement boardingPE = this.arrivalPathPerRouteStop[firstRouteStopIndex];
            double agentFirstArrivalTime = boardingPE.arrivalTime;
            int currentDepartureIndex = findNextDepartureIndex(route, firstRouteStop, agentFirstArrivalTime);
            if (currentDepartureIndex >= 0) {
                double currentDepartureTime = this.data.departures[currentDepartureIndex];
                double currentAgentBoardingTime;
                double currentCostWhenBoarding;
                {
                    double vehicleArrivalTime = currentDepartureTime + firstRouteStop.arrivalOffset;
                    currentAgentBoardingTime = (agentFirstArrivalTime < vehicleArrivalTime) ? vehicleArrivalTime : agentFirstArrivalTime;
                    double waitingTime = currentAgentBoardingTime - agentFirstArrivalTime;
                    double waitingCost = -this.config.getMarginalUtilityOfWaitingPt_utl_s() * waitingTime;
                    currentCostWhenBoarding = boardingPE.arrivalCost + waitingCost;
                }

                if (currentCostWhenBoarding > this.bestArrivalCost) {
                    continue;
                }
                routeIndex = tmpRouteIndex;

                for (int toRouteStopIndex = firstRouteStopIndex + 1; toRouteStopIndex < route.indexFirstRouteStop + route.countRouteStops; toRouteStopIndex++) {
                    RRouteStop toRouteStop = this.data.routeStops[toRouteStopIndex];
                    double arrivalTime = currentDepartureTime + toRouteStop.arrivalOffset;
                    double inVehicleTime = arrivalTime - currentAgentBoardingTime;
                    double inVehicleCost = inVehicleTime * -this.config.getMarginalUtilityOfTravelTimePt_utl_s();
                    double arrivalCost = currentCostWhenBoarding + inVehicleCost;
                    double previousArrivalCost = this.leastArrivalCostAtRouteStop[toRouteStopIndex];
                    if (arrivalCost < previousArrivalCost) {
                        PathElement pe = new PathElement(boardingPE, toRouteStop, arrivalTime, arrivalCost, boardingPE.transferCount, false);
                        this.arrivalPathPerRouteStop[toRouteStopIndex] = pe;
                        this.leastArrivalCostAtRouteStop[toRouteStopIndex] = arrivalCost;
                        if (arrivalCost < this.leastArrivalCostAtStop[toRouteStop.stopFacilityIndex]) {
                            this.leastArrivalCostAtStop[toRouteStop.stopFacilityIndex] = arrivalCost;
                            this.arrivalPathPerStop[toRouteStop.stopFacilityIndex] = pe;
                            this.improvedStops.set(toRouteStop.stopFacilityIndex);
                            checkForBestArrival(toRouteStopIndex, arrivalCost);
                        }
                    } else if (previousArrivalCost < arrivalCost) {
                        // looks like we could reach this stop with better cost from somewhere else
                        // check if we can depart also with better cost, if yes, switch to this connection
                        PathElement alternativeBoardingPE = this.arrivalPathPerRouteStop[toRouteStopIndex];
                        double alternativeAgentFirstArrivalTime = alternativeBoardingPE.arrivalTime;
                        int alternativeDepartureIndex = findNextDepartureIndex(route, toRouteStop, alternativeAgentFirstArrivalTime);
                        if (alternativeDepartureIndex >= 0) {
                            double alternativeDepartureTime = this.data.departures[alternativeDepartureIndex];
                            double alternativeVehicleArrivalTime = alternativeDepartureTime + toRouteStop.arrivalOffset;
                            double alternativeAgentBoardingTime = (alternativeAgentFirstArrivalTime < alternativeVehicleArrivalTime) ? alternativeVehicleArrivalTime : alternativeAgentFirstArrivalTime;
                            double alternativeWaitingTime = alternativeAgentBoardingTime - alternativeAgentFirstArrivalTime;
                            double alternativeWaitingCost = -this.config.getMarginalUtilityOfWaitingPt_utl_s() * alternativeWaitingTime;
                            double alternativeCostWhenBoarding = alternativeBoardingPE.arrivalCost + alternativeWaitingCost;
                            if (alternativeCostWhenBoarding < arrivalCost) {
                                currentDepartureIndex = alternativeDepartureIndex;
                                currentDepartureTime = alternativeDepartureTime;
                                currentAgentBoardingTime = alternativeAgentBoardingTime;
                                currentCostWhenBoarding = alternativeCostWhenBoarding;
                                boardingPE = alternativeBoardingPE;
                            }
                        }
                    }
                    firstRouteStopIndex = toRouteStopIndex; // we've handled this route stop, so we can skip it in the outer loop
                }
            }
        }
    }

    private void checkForBestArrival(int routeStopIndex, double arrivalCost) {
        if (this.destinationRouteStopIndices.get(routeStopIndex)) {
            // this is a destination stop
            double totalCost = arrivalCost + this.egressCostsPerRouteStop[routeStopIndex];
            if (totalCost < this.bestArrivalCost) {
                this.bestArrivalCost = totalCost;
            }
        }
    }

    private int findNextDepartureIndex(RRoute route, RRouteStop routeStop, double time) {
        double depTimeAtRouteStart = time - routeStop.departureOffset;
        int fromIndex = route.indexFirstDeparture;
        int toIndex = fromIndex + route.countDepartures;
        int pos = Arrays.binarySearch(this.data.departures, fromIndex, toIndex, depTimeAtRouteStart);
        if (pos < 0) {
            // binarySearch returns (-(insertion point) - 1) if the element was not found, which will happen most of the times.
            // insertion_point points to the next larger element, which is the next departure in our case
            // This can be transformed as follows:
            // retval = -(insertion point) - 1
            // ==> insertion point = -(retval+1) .
            pos = -(pos + 1);
        }
        if (pos >= toIndex) {
            // there is no later departure time
            return -1;
        }
        return pos;
    }

    private void handleTransfers() {
        this.improvedRouteStopIndices.clear();
        for (int stopIndex = this.improvedStops.nextSetBit(0); stopIndex >= 0; stopIndex = this.improvedStops.nextSetBit(stopIndex+1)) {
            PathElement fromPE = this.arrivalPathPerStop[stopIndex];
            double arrivalTime = fromPE.arrivalTime;
            double arrivalCost = fromPE.arrivalCost;
            if (arrivalCost > this.bestArrivalCost) {
                continue;
            }
            RRouteStop fromRouteStop = fromPE.toRouteStop; // this is the route stop we arrive with least cost at stop
            int firstTransferIndex = fromRouteStop.indexFirstTransfer;
            int lastTransferIndex = firstTransferIndex + fromRouteStop.countTransfers;
            for (int transferIndex = firstTransferIndex; transferIndex < lastTransferIndex; transferIndex++) {
                RTransfer transfer = this.data.transfers[transferIndex];
                int toRouteStopIndex = transfer.toRouteStop;
                double newArrivalCost = arrivalCost + transfer.transferCost;
                if (newArrivalCost < this.leastArrivalCostAtRouteStop[toRouteStopIndex]) {
                    double newArrivalTime = arrivalTime + transfer.transferTime;
                    RRouteStop toRouteStop = this.data.routeStops[toRouteStopIndex];
                    PathElement pe = new PathElement(fromPE, toRouteStop, newArrivalTime, newArrivalCost, fromPE.transferCount + 1, true);
                    this.arrivalPathPerRouteStop[toRouteStopIndex] = pe;
                    this.leastArrivalCostAtRouteStop[toRouteStopIndex] = newArrivalCost;
                    this.improvedRouteStopIndices.set(toRouteStopIndex);
                    int toStopFacilityIndex = toRouteStop.stopFacilityIndex;
                    if (newArrivalCost < this.leastArrivalCostAtStop[toStopFacilityIndex]) {
                        this.leastArrivalCostAtStop[toStopFacilityIndex] = newArrivalCost;
                        this.arrivalPathPerStop[toStopFacilityIndex] = pe;
                    }
                }
            }
        }
    }

    private PathElement findLeastCostArrival(Map<TransitStopFacility, InitialStop> destinationStops) {
        double leastCost = Double.POSITIVE_INFINITY;
        PathElement leastCostPath = null;

        for (Map.Entry<TransitStopFacility, InitialStop> e : destinationStops.entrySet()) {
            TransitStopFacility stop = e.getKey();
            int stopIndex = this.data.stopFacilityIndices.get(stop);
            PathElement pe = this.arrivalPathPerStop[stopIndex];
            if (pe != null) {
                InitialStop egressStop = e.getValue();
                double arrivalTime = pe.arrivalTime + egressStop.accessTime;
                double totalCost = pe.arrivalCost + egressStop.accessCost;
                if ((totalCost < leastCost) || (totalCost == leastCost && pe.transferCount < leastCostPath.transferCount)) {
                    leastCost = totalCost;
                    leastCostPath = new PathElement(pe, null, arrivalTime, totalCost, pe.transferCount, true); // this is the egress leg
                }
            }
        }
        return leastCostPath;
    }

    private RaptorRoute createRaptorRoute(PathElement destinationPathElement, double departureTime) {
        LinkedList<PathElement> pes = new LinkedList<>();
        double arrivalCost = Double.POSITIVE_INFINITY;
        if (destinationPathElement != null) {
            arrivalCost = destinationPathElement.arrivalCost;
            PathElement pe = destinationPathElement;
            while (pe.comingFrom != null) {
                pes.addFirst(pe);
                pe = pe.comingFrom;
            }
            pes.addFirst(pe);
        }

        RaptorRoute raptorRoute = new RaptorRoute(null, null, arrivalCost);
        double time = departureTime;
        TransitStopFacility fromStop = null;
        int peCount = pes.size();
        int i = -1;
        for (PathElement pe : pes) {
            i++;
            TransitStopFacility toStop = pe.toRouteStop == null ? null : pe.toRouteStop.routeStop.getStopFacility();
            double travelTime = pe.arrivalTime - time;
            if (pe.isTransfer) {
                boolean differentFromTo = (fromStop == null || toStop == null) || (fromStop != toStop);
                if (differentFromTo) {
                    if (i == peCount - 2) {
                        // the second last element is a transfer, skip it so it gets merged into the egress_walk
                        continue;
                    }
                    // do not create a transfer-leg if we stay at the same stop facility
                    String mode = TransportMode.transit_walk;
                    if (fromStop == null && toStop != null) {
                        mode = TransportMode.access_walk;
                    }
                    if (fromStop != null && toStop == null) {
                        mode = TransportMode.egress_walk;
                    }
                    raptorRoute.addNonPt(fromStop, toStop, time, travelTime, mode);
                }
            } else {
                TransitLine line = pe.toRouteStop.line;
                TransitRoute route = pe.toRouteStop.route;
                raptorRoute.addPt(fromStop, toStop, line, route, time, travelTime);
            }
            time = pe.arrivalTime;
            fromStop = toStop;
        }
        return raptorRoute;
    }

    private static class PathElement {
        PathElement comingFrom;
        RRouteStop toRouteStop;
        double arrivalTime;
        double arrivalCost;
        int transferCount;
        boolean isTransfer;

        PathElement(PathElement comingFrom, RRouteStop toRouteStop, double arrivalTime, double arrivalCost, int transferCount, boolean isTransfer) {
            this.comingFrom = comingFrom;
            this.toRouteStop = toRouteStop;
            this.arrivalTime = arrivalTime;
            this.arrivalCost = arrivalCost;
            this.transferCount = transferCount;
            this.isTransfer = isTransfer;
        }
    }
}
