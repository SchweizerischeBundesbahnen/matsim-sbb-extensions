/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mrieser / SBB
 */
class SwissRailRaptorData {

    private static final Logger log = Logger.getLogger(SwissRailRaptorData.class);

    final RaptorConfig config;
    final int countStops;
    final int countRouteStops;
    final RRoute[] routes;
    final double[] departures; // in the RAPTOR paper, this is usually called "trips", but I stick with the MATSim nomenclature
    final RRouteStop[] routeStops; // list of all route stops
    final RTransfer[] transfers;
    final Map<TransitStopFacility, Integer> stopFacilityIndices;
    final Map<TransitStopFacility, int[]> routeStopsPerStopFacility;
    final QuadTree<TransitStopFacility> stopsQT;

    private SwissRailRaptorData(RaptorConfig config, int countStops,
                                RRoute[] routes, double[] departures, RRouteStop[] routeStops,
                                RTransfer[] transfers, Map<TransitStopFacility, Integer> stopFacilityIndices,
                                Map<TransitStopFacility, int[]> routeStopsPerStopFacility, QuadTree<TransitStopFacility> stopsQT) {
        this.config = config;
        this.countStops = countStops;
        this.countRouteStops = routeStops.length;
        this.routes = routes;
        this.departures = departures;
        this.routeStops = routeStops;
        this.transfers = transfers;
        this.stopFacilityIndices = stopFacilityIndices;
        this.routeStopsPerStopFacility = routeStopsPerStopFacility;
        this.stopsQT = stopsQT;
    }

    public static SwissRailRaptorData create(TransitSchedule schedule, RaptorConfig config) {
        log.info("Preparing data for SwissRailRaptor...");
        long startMillis = System.currentTimeMillis();

        int countRoutes = 0;
        long countRouteStops = 0;
        long countDepartures = 0;

        for (TransitLine line : schedule.getTransitLines().values()) {
            countRoutes += line.getRoutes().size();
            for (TransitRoute route : line.getRoutes().values()) {
                countRouteStops += route.getStops().size();
                countDepartures += route.getDepartures().size();
            }
        }

        if (countRouteStops > Integer.MAX_VALUE) {
            throw new RuntimeException("TransitSchedule has too many TransitRouteStops: " + countRouteStops);
        }
        if (countDepartures > Integer.MAX_VALUE) {
            throw new RuntimeException("TransitSchedule has too many Departures: " + countDepartures);
        }

        double[] departures = new double[(int) countDepartures];
        RRoute[] routes = new RRoute[countRoutes];
        RRouteStop[] routeStops = new RRouteStop[(int) countRouteStops];

        int indexRoutes = 0;
        int indexRouteStops = 0;
        int indexDeparture = 0;

        // enumerate TransitStopFacilities along their usage in transit routes to (hopefully) achieve a better memory locality
        // well, I'm not even sure how often we'll need the transit stop facilities, likely we'll use RouteStops more often
        Map<TransitStopFacility, Integer> stopFacilityIndices = new HashMap<>((int) (schedule.getFacilities().size() * 1.5));
        Map<TransitStopFacility, int[]> routeStopsPerStopFacility = new HashMap<>();

        for (TransitLine line : schedule.getTransitLines().values()) {
            List<TransitRoute> transitRoutes = new ArrayList<>(line.getRoutes().values());
            transitRoutes.sort((tr1, tr2) -> Double.compare(getEarliestDeparture(tr1).getDepartureTime(), getEarliestDeparture(tr2).getDepartureTime())); // sort routes by earliest departure for additional performance gains
            for (TransitRoute route : transitRoutes) {
                int indexFirstDeparture = indexDeparture;
                RRoute rroute = new RRoute(indexRouteStops, route.getStops().size(), indexFirstDeparture, route.getDepartures().size());
                routes[indexRoutes] = rroute;
                for (TransitRouteStop routeStop : route.getStops()) {
                    int stopFacilityIndex = stopFacilityIndices.computeIfAbsent(routeStop.getStopFacility(), stop -> stopFacilityIndices.size());
                    RRouteStop rRouteStop = new RRouteStop(routeStop, line, route, indexRoutes, stopFacilityIndex);
                    final int thisRouteStopIndex = indexRouteStops;
                    routeStops[thisRouteStopIndex] = rRouteStop;
                    routeStopsPerStopFacility.compute(routeStop.getStopFacility(), (stop, currentRouteStops) -> {
                        if (currentRouteStops == null) {
                            return new int[] { thisRouteStopIndex };
                        }
                        int[] tmp = new int[currentRouteStops.length + 1];
                        System.arraycopy(currentRouteStops, 0, tmp, 0, currentRouteStops.length);
                        tmp[currentRouteStops.length] = thisRouteStopIndex;
                        return tmp;
                    });
                    indexRouteStops++;
                }
                for (Departure dep : route.getDepartures().values()) {
                    departures[indexDeparture] = dep.getDepartureTime();
                    indexDeparture++;
                }
                Arrays.sort(departures, indexFirstDeparture, indexDeparture);
                indexRoutes++;
            }
        }

        // only put used transit stops into the quad tree
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        Set<TransitStopFacility> stops = routeStopsPerStopFacility.keySet();
        for (TransitStopFacility stopFacility : stops) {
            double x = stopFacility.getCoord().getX();
            double y = stopFacility.getCoord().getY();

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        QuadTree<TransitStopFacility> stopsQT = new QuadTree<>(minX, minY, maxX, maxY);
        for (TransitStopFacility stopFacility : stops) {
            double x = stopFacility.getCoord().getX();
            double y = stopFacility.getCoord().getY();
            stopsQT.put(x, y, stopFacility);
        }
        int countStopFacilities = stops.size();

        Map<Integer, RTransfer[]> allTransfers = calculateRouteStopTransfers(schedule, stopsQT, routeStopsPerStopFacility, routeStops, config);
        long countTransfers = 0;
        for (RTransfer[] transfers : allTransfers.values()) {
            countTransfers += transfers.length;
        }
        if (countTransfers > Integer.MAX_VALUE) {
            throw new RuntimeException("TransitSchedule has too many Transfers: " + countTransfers);
        }
        RTransfer[] transfers = new RTransfer[(int) countTransfers];
        int indexTransfer = 0;
        for (int routeStopIndex = 0; routeStopIndex < routeStops.length; routeStopIndex++) {
            RTransfer[] stopTransfers = allTransfers.get(routeStopIndex);
            int transferCount = stopTransfers == null ? 0 : stopTransfers.length;
            if (transferCount > 0) {
                RRouteStop routeStop = routeStops[routeStopIndex];
                routeStop.indexFirstTransfer = indexTransfer;
                routeStop.countTransfers = transferCount;
                System.arraycopy(stopTransfers, 0, transfers, indexTransfer, transferCount);
                indexTransfer += transferCount;
            }
        }

        SwissRailRaptorData data = new SwissRailRaptorData(config, countStopFacilities, routes, departures, routeStops, transfers, stopFacilityIndices, routeStopsPerStopFacility, stopsQT);

        long endMillis = System.currentTimeMillis();
        log.info("SwissRailRaptor data preparation done. Took " + (endMillis - startMillis) / 1000 + " seconds.");
        log.info("SwissRailRaptor statistics:  #routes = " + routes.length);
        log.info("SwissRailRaptor statistics:  #departures = " + departures.length);
        log.info("SwissRailRaptor statistics:  #routeStops = " + routeStops.length);
        log.info("SwissRailRaptor statistics:  #stopFacilities = " + countStopFacilities);
        log.info("SwissRailRaptor statistics:  #transfers (between routeStops) = " + transfers.length);
        return data;
    }

    // calculate possible transfers between TransitRouteStops
    private static Map<Integer, RTransfer[]> calculateRouteStopTransfers(TransitSchedule schedule, QuadTree<TransitStopFacility> stopsQT, Map<TransitStopFacility, int[]> routeStopsPerStopFacility, RRouteStop[] routeStops, RaptorConfig config) {
        Map<Integer, RTransfer[]> transfers = new HashMap<>(stopsQT.size() * 5);
        double maxBeelineWalkConnectionDistance = config.getBeelineWalkConnectionDistance();
        double beelineWalkSpeed = config.getBeelineWalkSpeed();
        double transferUtilPerS = config.getMarginalUtilityOfTravelTimeWalk_utl_s();
        double transferPenalty = config.getTransferPenaltyCost();
        double minimalTransferTime = config.getMinimalTransferTime();

        Map<TransitStopFacility, List<TransitStopFacility>> stopToStopsTransfers = new HashMap<>();

        // first, add transfers based on distance
        for (TransitStopFacility fromStop : routeStopsPerStopFacility.keySet()) {
            Coord fromCoord = fromStop.getCoord();
            Collection<TransitStopFacility> nearbyStops = stopsQT.getDisk(fromCoord.getX(), fromCoord.getY(), maxBeelineWalkConnectionDistance);
            stopToStopsTransfers.computeIfAbsent(fromStop, stop -> new ArrayList<>(5)).addAll(nearbyStops);
        }

        // take the transfers from the schedule into account
        MinimalTransferTimes.MinimalTransferTimesIterator iter = schedule.getMinimalTransferTimes().iterator();
        while (iter.hasNext()) {
            iter.next();
            Id<TransitStopFacility> fromStopId = iter.getFromStopId();
            TransitStopFacility fromStop = schedule.getFacilities().get(fromStopId);
            Id<TransitStopFacility> toStopId = iter.getToStopId();
            TransitStopFacility toStop = schedule.getFacilities().get(toStopId);
            List<TransitStopFacility> destinationStops = stopToStopsTransfers.computeIfAbsent(fromStop, stop -> new ArrayList<>(5));
            if (!destinationStops.contains(toStop)) {
                destinationStops.add(toStop);
            }
        }

        // now calculate the transfers between the route stops
        MinimalTransferTimes mtt = schedule.getMinimalTransferTimes();
        for (Map.Entry<TransitStopFacility, List<TransitStopFacility>> e : stopToStopsTransfers.entrySet()) {
            TransitStopFacility fromStop = e.getKey();
            Coord fromCoord = fromStop.getCoord();
            int[] fromRouteStopIndices = routeStopsPerStopFacility.get(fromStop);
            Collection<TransitStopFacility> nearbyStops = e.getValue();
            for (TransitStopFacility toStop : nearbyStops) {
                int[] toRouteStopIndices = routeStopsPerStopFacility.get(toStop);
                double distance = CoordUtils.calcEuclideanDistance(fromCoord, toStop.getCoord());
                double transferTime = distance / beelineWalkSpeed;
                if (transferTime < minimalTransferTime) {
                    transferTime = minimalTransferTime;
                }

                transferTime = mtt.get(fromStop.getId(), toStop.getId(), transferTime);

                double transferUtil = transferTime * transferUtilPerS;
                double transferCost = -transferUtil + transferPenalty;
                final double fixedTransferTime = transferTime; // variables must be effective final to be used in lambdas (below)

                for (int fromRouteStopIndex : fromRouteStopIndices) {
                    RRouteStop fromRouteStop = routeStops[fromRouteStopIndex];
                    for (int toRouteStopIndex : toRouteStopIndices) {
                        RRouteStop toRouteStop = routeStops[toRouteStopIndex];
                        if (isUsefulTransfer(fromRouteStop, toRouteStop, config.getBeelineWalkConnectionDistance())) {
                            transfers.compute(fromRouteStopIndex, (routeStopIndex, currentTransfers) -> {
                                RTransfer newTransfer = new RTransfer(fromRouteStopIndex, toRouteStopIndex, fixedTransferTime, transferCost);
                                if (currentTransfers == null) {
                                    return new RTransfer[] { newTransfer };
                                }
                                RTransfer[] tmp = new RTransfer[currentTransfers.length + 1];
                                System.arraycopy(currentTransfers, 0, tmp, 0, currentTransfers.length);
                                tmp[currentTransfers.length] = newTransfer;
                                return tmp;
                            });
                        }
                    }
                }
            }
        }
        return transfers;
    }

    private static boolean isUsefulTransfer(RRouteStop fromRouteStop, RRouteStop toRouteStop, double maxDistance) {
        if (fromRouteStop == toRouteStop) {
            return false;
        }
        // there is no use to transfer away from the first stop in a route
        if (isFirstStopInRoute(fromRouteStop)) {
            return false;
        }
        // there is no use to transfer to the last stop in a route, we can't go anywhere from there
        if (isLastStopInRoute(toRouteStop)) {
            return false;
        }
        // if the first departure at fromRouteStop arrives after the last departure at toRouteStop,
        // we'll never get any connection here
        if (hasNoPossibleDeparture(fromRouteStop, toRouteStop)) {
            return false;
        }
        // if the stop facilities are different, and the destination stop is part
        // of the current route, it does not make sense to transfer here
        if (toStopIsPartOfRouteButNotSame(fromRouteStop, toRouteStop)) {
            return false;
        }
        // assuming vehicles serving the exact same stop sequence do not overtake each other,
        // it does not make sense to transfer to another route that serves the exact same upcoming stops
        if (cannotReachAdditionalStops(fromRouteStop, toRouteStop)) {
            return false;
        }
        // If one could have transferred to the same route one stop before, it does not make sense
        // to transfer here.
        if (couldHaveTransferredOneStopEarlierInOppositeDirection(fromRouteStop, toRouteStop, maxDistance)) {
            return false;
        }
        // if we failed all other checks, it looks like this transfer is useful
        return true;
    }

    private static boolean isFirstStopInRoute(RRouteStop routeStop) {
        TransitRouteStop firstRouteStop = routeStop.route.getStops().get(0);
        return routeStop.routeStop == firstRouteStop;
    }

    private static boolean isLastStopInRoute(RRouteStop routeStop) {
        List<TransitRouteStop> routeStops = routeStop.route.getStops();
        TransitRouteStop lastRouteStop = routeStops.get(routeStops.size() - 1);
        return routeStop.routeStop == lastRouteStop;
    }

    private static boolean hasNoPossibleDeparture(RRouteStop fromRouteStop, RRouteStop toRouteStop) {
        Departure earliestDep = getEarliestDeparture(fromRouteStop.route);
        Departure latestDep = getLatestDeparture(toRouteStop.route);
        if (earliestDep == null || latestDep == null) {
            return true;
        }
        double earliestArrival = earliestDep.getDepartureTime() + fromRouteStop.arrivalOffset;
        double latestDeparture = latestDep.getDepartureTime() + toRouteStop.departureOffset;
        return earliestArrival > latestDeparture;
    }

    private static Departure getEarliestDeparture(TransitRoute route) {
        Departure earliest = null;
        for (Departure dep : route.getDepartures().values()) {
            if (earliest == null || dep.getDepartureTime() < earliest.getDepartureTime()) {
                earliest = dep;
            }
        }
        return earliest;
    }

    private static Departure getLatestDeparture(TransitRoute route) {
        Departure latest = null;
        for (Departure dep : route.getDepartures().values()) {
            if (latest == null || dep.getDepartureTime() > latest.getDepartureTime()) {
                latest = dep;
            }
        }
        return latest;
    }

    private static boolean toStopIsPartOfRouteButNotSame(RRouteStop fromRouteStop, RRouteStop toRouteStop) {
        TransitStopFacility fromStopFacility = fromRouteStop.routeStop.getStopFacility();
        TransitStopFacility toStopFacility = toRouteStop.routeStop.getStopFacility();
        if (fromStopFacility == toStopFacility) {
            return false;
        }
        for (TransitRouteStop routeStop : fromRouteStop.route.getStops()) {
            fromStopFacility = routeStop.getStopFacility();
            if (fromStopFacility == toStopFacility) {
                return true;
            }
        }
        return false;
    }

    private static boolean cannotReachAdditionalStops(RRouteStop fromRouteStop, RRouteStop toRouteStop) {
        Iterator<TransitRouteStop> fromIter = fromRouteStop.route.getStops().iterator();
        while (fromIter.hasNext()) {
            TransitRouteStop routeStop = fromIter.next();
            if (fromRouteStop.routeStop == routeStop) {
                break;
            }
        }
        Iterator<TransitRouteStop> toIter = toRouteStop.route.getStops().iterator();
        while (toIter.hasNext()) {
            TransitRouteStop routeStop = toIter.next();
            if (toRouteStop.routeStop == routeStop) {
                break;
            }
        }
        // both iterators now point to the route stops where the potential transfer happens
        while (true) {
            boolean fromRouteHasNext = fromIter.hasNext();
            boolean toRouteHasNext = toIter.hasNext();
            if (!toRouteHasNext) {
                // there are no more stops in the toRoute
                return true;
            }
            if (!fromRouteHasNext) {
                // there are no more stops in the fromRoute, but there are in the toRoute
                return false;
            }
            TransitRouteStop fromStop = fromIter.next();
            TransitRouteStop toStop = toIter.next();
            if (fromStop.getStopFacility() != toStop.getStopFacility()) {
                // the toRoute goes to a different stop
                return false;
            }
        }
    }

    private static boolean couldHaveTransferredOneStopEarlierInOppositeDirection(RRouteStop fromRouteStop, RRouteStop toRouteStop, double maxDistance) {
        TransitRouteStop previousRouteStop = null;
        for (TransitRouteStop routeStop : fromRouteStop.route.getStops()) {
            if (fromRouteStop.routeStop == routeStop) {
                break;
            }
            previousRouteStop = routeStop;
        }
        if (previousRouteStop == null) {
            return false;
        }

        Iterator<TransitRouteStop> toIter = toRouteStop.route.getStops().iterator();
        while (toIter.hasNext()) {
            TransitRouteStop routeStop = toIter.next();
            if (toRouteStop.routeStop == routeStop) {
                break;
            }
        }
        boolean toRouteHasNext = toIter.hasNext();
        if (!toRouteHasNext) {
            return false;
        }

        TransitRouteStop toStop = toIter.next();
        if (previousRouteStop.getStopFacility() == toStop.getStopFacility()) {
            return true;
        }

        double distance = CoordUtils.calcEuclideanDistance(previousRouteStop.getStopFacility().getCoord(), toStop.getStopFacility().getCoord());
        if (distance < maxDistance) {
            return true;
        }
        return false;
    }

    static final class RRoute {
        final int indexFirstRouteStop;
        final int countRouteStops;
        final int indexFirstDeparture;
        final int countDepartures;

        RRoute(int indexFirstRouteStop, int countRouteStops, int indexFirstDeparture, int countDepartures) {
            this.indexFirstRouteStop = indexFirstRouteStop;
            this.countRouteStops = countRouteStops;
            this.indexFirstDeparture = indexFirstDeparture;
            this.countDepartures = countDepartures;
        }
    }

    static final class RRouteStop {
        final TransitRouteStop routeStop;
        final TransitLine line;
        final TransitRoute route;
        final int transitRouteIndex;
        final int stopFacilityIndex;
        final double arrivalOffset;
        final double departureOffset;
        int indexFirstTransfer = -1;
        int countTransfers = 0;

        RRouteStop(TransitRouteStop routeStop, TransitLine line, TransitRoute route, int transitRouteIndex, int stopFacilityIndex) {
            this.routeStop = routeStop;
            this.line = line;
            this.route = route;
            this.transitRouteIndex = transitRouteIndex;
            this.stopFacilityIndex = stopFacilityIndex;
            // "normalize" the arrival and departure offsets, make sure they are always well defined.
            this.arrivalOffset = isUndefinedTime(routeStop.getArrivalOffset()) ? routeStop.getDepartureOffset() : routeStop.getArrivalOffset();
            this.departureOffset = isUndefinedTime(routeStop.getDepartureOffset()) ? routeStop.getArrivalOffset() : routeStop.getDepartureOffset();
        }

        private static boolean isUndefinedTime(double time) {
            return time == Time.UNDEFINED_TIME || Double.isNaN(time);
        }
    }

    static final class RTransfer {
        final int fromRouteStop;
        final int toRouteStop;
        final double transferTime;
        final double transferCost;

        RTransfer(int fromRouteStop, int toRouteStop, double transferTime, double transferCost) {
            this.fromRouteStop = fromRouteStop;
            this.toRouteStop = toRouteStop;
            this.transferTime = transferTime;
            this.transferCost = transferCost;
        }
    }
}
