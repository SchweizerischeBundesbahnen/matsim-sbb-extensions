/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides public transport route search capabilities using an implementation of the
 * RAPTOR algorithm underneath.
 *
 * @author mrieser / SBB
 */
public class SwissRailRaptor implements TransitRouter {

    private static final Logger log = Logger.getLogger(SwissRailRaptor.class);

    private final SwissRailRaptorData data;
    private final SwissRailRaptorCore raptor;
    private final RaptorConfig config;
    private final RaptorRouteSelector defaultRouteSelector;

    public SwissRailRaptor(final SwissRailRaptorData data, RaptorRouteSelector routeSelector) {
        this.data = data;
        this.config = data.config;
        this.raptor = new SwissRailRaptorCore(data);
        this.defaultRouteSelector = routeSelector;
    }

    @Override
    public List<Leg> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double departureTime, Person person) {
        List<InitialStop> accessStops = findAccessStops(fromFacility, person);
        List<InitialStop> egressStops = findEgressStops(toFacility, person);

        RaptorRoute foundRoute = this.raptor.calcLeastCostRoute(departureTime, accessStops, egressStops);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, departureTime, person);

        if (foundRoute == null || directWalk.getTotalCosts() < foundRoute.getTotalCosts()) {
            foundRoute = directWalk;
        }
        List<Leg> legs = RaptorUtils.convertRouteToLegs(foundRoute);
        return legs;
    }

    public List<Leg> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double earliestDepartureTime, double desiredDepartureTime, double latestDepartureTime, Person person) {
        return calcRoute(fromFacility, toFacility, earliestDepartureTime, desiredDepartureTime, latestDepartureTime, person, this.defaultRouteSelector);
    }

    public List<Leg> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double earliestDepartureTime, double desiredDepartureTime, double latestDepartureTime, Person person, RaptorRouteSelector selector) {
        List<InitialStop> accessStops = findAccessStops(fromFacility, person);
        List<InitialStop> egressStops = findEgressStops(toFacility, person);

        List<RaptorRoute> foundRoutes = this.raptor.calcRoutes(earliestDepartureTime, desiredDepartureTime, latestDepartureTime, accessStops, egressStops);
        RaptorRoute foundRoute = selector.selectOne(foundRoutes, desiredDepartureTime);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, desiredDepartureTime, person);

        if (foundRoute == null || directWalk.getTotalCosts() < foundRoute.getTotalCosts()) {
            foundRoute = directWalk;
        }
        List<Leg> legs = RaptorUtils.convertRouteToLegs(foundRoute);
        return legs;
    }

    public List<RaptorRoute> calcRoutes(Facility<?> fromFacility, Facility<?> toFacility, double earliestDepartureTime, double desiredDepartureTime, double latestDepartureTime, Person person) {
        List<InitialStop> accessStops = findAccessStops(fromFacility, person);
        List<InitialStop> egressStops = findEgressStops(toFacility, person);

        List<RaptorRoute> foundRoutes = this.raptor.calcRoutes(earliestDepartureTime, desiredDepartureTime, latestDepartureTime, accessStops, egressStops);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, desiredDepartureTime, person);

        if (foundRoutes == null || foundRoutes.isEmpty() || directWalk.getTotalCosts() < foundRoutes.get(0).getTotalCosts()) {
            foundRoutes.add(directWalk); // add direct walk if it seems plausible
        }
        return foundRoutes;
    }

    private List<InitialStop> findAccessStops(Facility<?> facility, Person person) {
            List<TransitStopFacility> stops = findNearbyStops(facility);
            List<InitialStop> initialStops = stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / this.config.getBeelineWalkSpeed());
                double disutility = travelTime * -this.config.getMarginalUtilityOfTravelTimeAccessWalk_utl_s();
                return new InitialStop(stop, disutility, travelTime, beelineDistance, TransportMode.access_walk);
            }).collect(Collectors.toList());
            return initialStops;
    }

    private List<InitialStop> findEgressStops(Facility<?> facility, Person person) {
            List<TransitStopFacility> stops = findNearbyStops(facility);
            List<InitialStop> initialStops = stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / this.config.getBeelineWalkSpeed());
                double disutility = travelTime * -this.config.getMarginalUtilityOfTravelTimeEgressWalk_utl_s();
                return new InitialStop(stop, disutility, travelTime, beelineDistance, TransportMode.egress_walk);
            }).collect(Collectors.toList());
        return initialStops;
    }

    private List<TransitStopFacility> findNearbyStops(Facility<?> facility) {
        double x = facility.getCoord().getX();
        double y = facility.getCoord().getY();
        Collection<TransitStopFacility> stopFacilities = this.data.stopsQT.getDisk(x, y, this.config.getSearchRadius());
        if (stopFacilities.size() < 2) {
            TransitStopFacility  nearestStop = this.data.stopsQT.getClosest(x, y);
            double nearestDistance = CoordUtils.calcEuclideanDistance(facility.getCoord(), nearestStop.getCoord());
            stopFacilities = this.data.stopsQT.getDisk(x, y, nearestDistance + this.config.getExtensionRadius());
        }
        if (stopFacilities instanceof List) {
            return (List<TransitStopFacility>) stopFacilities;
        }
        return new ArrayList<>(stopFacilities);
    }

    private RaptorRoute createDirectWalk(Facility<?> fromFacility, Facility<?> toFacility, double departureTime, Person person) {
        double beelineDistance = CoordUtils.calcEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());
        double walkTime = beelineDistance / this.config.getBeelineWalkSpeed();
        double walkCost_per_s = -this.config.getMarginalUtilityOfTravelTimeWalk_utl_s();
        double walkCost = walkTime * walkCost_per_s;

        RaptorRoute route = new RaptorRoute(fromFacility, toFacility, walkCost);
        route.addNonPt(null, null, departureTime, walkTime, TransportMode.transit_walk);
        return route;
    }

}
