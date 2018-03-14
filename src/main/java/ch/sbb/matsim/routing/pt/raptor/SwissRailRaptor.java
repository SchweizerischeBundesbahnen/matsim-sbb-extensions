/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final RaptorParameters parameters;
    private final RaptorParametersForPerson parametersForPerson;
    private final RaptorRouteSelector defaultRouteSelector;
    private final String subpopulationAttribute;
    private final ObjectAttributes personAttributes;
    private final TripRouter tripRouter;

    public SwissRailRaptor(final SwissRailRaptorData data, RaptorParametersForPerson parametersForPerson, RaptorRouteSelector routeSelector) {
        this(data, parametersForPerson, routeSelector, null, null, null);
        log.info("SwissRailRaptor was initialized without support for subpopulations or intermodal access/egress legs.");
    }

    public SwissRailRaptor(final SwissRailRaptorData data, RaptorParametersForPerson parametersForPerson, RaptorRouteSelector routeSelector,
                           String subpopulationAttribute, ObjectAttributes personAttributes, TripRouter tripRouter) {
        this.data = data;
        this.parameters = data.config;
        this.raptor = new SwissRailRaptorCore(data);
        this.parametersForPerson = parametersForPerson;
        this.defaultRouteSelector = routeSelector;
        this.subpopulationAttribute = subpopulationAttribute;
        this.personAttributes = personAttributes;
        this.tripRouter = tripRouter;
    }

    @Override
    public List<Leg> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double departureTime, Person person) {
        RaptorParameters config = this.parametersForPerson.getRaptorParameters(person);
        List<InitialStop> accessStops = findAccessStops(fromFacility, person, departureTime);
        List<InitialStop> egressStops = findEgressStops(toFacility, person, departureTime);

        RaptorRoute foundRoute = this.raptor.calcLeastCostRoute(departureTime, accessStops, egressStops, config);
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
        RaptorParameters config = this.parametersForPerson.getRaptorParameters(person);
        List<InitialStop> accessStops = findAccessStops(fromFacility, person, desiredDepartureTime);
        List<InitialStop> egressStops = findEgressStops(toFacility, person, desiredDepartureTime);

        List<RaptorRoute> foundRoutes = this.raptor.calcRoutes(earliestDepartureTime, desiredDepartureTime, latestDepartureTime, accessStops, egressStops, config);
        RaptorRoute foundRoute = selector.selectOne(foundRoutes, desiredDepartureTime);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, desiredDepartureTime, person);

        if (foundRoute == null || directWalk.getTotalCosts() < foundRoute.getTotalCosts()) {
            foundRoute = directWalk;
        }
        List<Leg> legs = RaptorUtils.convertRouteToLegs(foundRoute);
        return legs;
    }

    public List<RaptorRoute> calcRoutes(Facility<?> fromFacility, Facility<?> toFacility, double earliestDepartureTime, double desiredDepartureTime, double latestDepartureTime, Person person) {
        List<InitialStop> accessStops = findAccessStops(fromFacility, person, desiredDepartureTime);
        List<InitialStop> egressStops = findEgressStops(toFacility, person, desiredDepartureTime);

        List<RaptorRoute> foundRoutes = this.raptor.calcRoutes(earliestDepartureTime, desiredDepartureTime, latestDepartureTime, accessStops, egressStops, parameters);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, desiredDepartureTime, person);

        if (foundRoutes == null) {
            foundRoutes = new ArrayList<>(1);
        }
        if (foundRoutes.isEmpty() || directWalk.getTotalCosts() < foundRoutes.get(0).getTotalCosts()) {
            foundRoutes.add(directWalk); // add direct walk if it seems plausible
        }
        return foundRoutes;
    }

    private List<InitialStop> findAccessStops(Facility<?> facility, Person person, double departureTime) {
        SwissRailRaptorConfigGroup srrCfg = this.parameters.getConfig();
        if (srrCfg.isUseIntermodalAccessEgress()) {
            return findIntermodalStops(facility, person, departureTime, Direction.Access);
        } else {
            List<TransitStopFacility> stops = findNearbyStops(facility);
            List<InitialStop> initialStops = stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / this.parameters.getBeelineWalkSpeed());
                double disutility = travelTime * -this.parameters.getMarginalUtilityOfTravelTimeAccessWalk_utl_s();
                return new InitialStop(stop, disutility, travelTime, beelineDistance, TransportMode.access_walk);
            }).collect(Collectors.toList());
            return initialStops;
        }
    }

    private List<InitialStop> findEgressStops(Facility<?> facility, Person person, double departureTime) {
        SwissRailRaptorConfigGroup srrCfg = this.parameters.getConfig();
        if (srrCfg.isUseIntermodalAccessEgress()) {
            return findIntermodalStops(facility, person, departureTime, Direction.Egress);
        } else {
            List<TransitStopFacility> stops = findNearbyStops(facility);
            List<InitialStop> initialStops = stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / this.parameters.getBeelineWalkSpeed());
                double disutility = travelTime * -this.parameters.getMarginalUtilityOfTravelTimeEgressWalk_utl_s();
                return new InitialStop(stop, disutility, travelTime, beelineDistance, TransportMode.egress_walk);
            }).collect(Collectors.toList());
            return initialStops;
        }
    }

    private enum Direction { Access, Egress }

    private List<InitialStop> findIntermodalStops(Facility<?> facility, Person person, double departureTime, Direction direction) {
        SwissRailRaptorConfigGroup srrCfg = this.parameters.getConfig();
        double x = facility.getCoord().getX();
        double y = facility.getCoord().getY();
        String personId = person.getId().toString();
        List<InitialStop> initialStops = new ArrayList<>();
        for (IntermodalAccessEgressParameterSet paramset : srrCfg.getIntermodalAccessEgressParameterSets()) {
            double radius = paramset.getRadius();
            String mode = paramset.getMode();
            String overrideMode = null;
            if (mode.equals(TransportMode.walk) || mode.equals(TransportMode.transit_walk)) {
                overrideMode = direction == Direction.Access ? TransportMode.access_walk : TransportMode.egress_walk;
            }
            Set<String> subPops = paramset.getSubpopulations();
            String linkIdAttribute = paramset.getLinkIdAttribute();
            String filterAttribute = paramset.getFilterAttribute();
            String filterValue = paramset.getFilterValue();

            boolean subpopMatches = true;
            if (subPops != null && !subPops.isEmpty() && this.subpopulationAttribute != null) {
                Object attr = this.personAttributes.getAttribute(personId, this.subpopulationAttribute);
                String attrValue = attr == null ? null : attr.toString();
                subpopMatches = subPops.contains(attrValue);
            }

            if (subpopMatches) {
                Collection<TransitStopFacility> stopFacilities = this.data.stopsQT.getDisk(x, y, radius);
                for (TransitStopFacility stop : stopFacilities) {
                    boolean filterMatches = true;
                    if (filterAttribute != null) {
                        Object attr = stop.getAttributes().getAttribute(filterAttribute);
                        String attrValue = attr == null ? null : attr.toString();
                        filterMatches = filterValue.equals(attrValue);
                    }
                    if (filterMatches) {
                        Facility<TransitStopFacility> stopFacility = stop;
                        if (linkIdAttribute != null) {
                            Object attr = stop.getAttributes().getAttribute(linkIdAttribute);
                            if (attr != null) {
                                stopFacility = new ChangedLinkFacility(stop, Id.create(attr.toString(), Link.class));
                            }
                        }

                        List<? extends PlanElement> routeParts;
                        if (direction == Direction.Access) {
                            routeParts = this.tripRouter.calcRoute(mode, facility, stopFacility, departureTime, person);
                        } else { // it's Egress
                            // We don't know the departure time for the egress trip, so just use the original departureTime,
                            // although it is wrong and might result in a wrong traveltime and thus wrong route.
                            routeParts = this.tripRouter.calcRoute(mode, stopFacility, facility, departureTime, person);
                            // clear the (wrong) departureTime so users don't get confused
                            for (PlanElement pe : routeParts) {
                                if (pe instanceof Leg) {
                                    ((Leg) pe).setDepartureTime(Time.UNDEFINED_TIME);
                                }
                            }
                        }
                        if (overrideMode != null) {
                            for (PlanElement pe : routeParts) {
                                if (pe instanceof Leg) {
                                    ((Leg) pe).setMode(overrideMode);
                                }
                            }
                        }
                        if (stopFacility != stop) {
                            if (direction == Direction.Access) {
                                Leg transferLeg = PopulationUtils.createLeg(TransportMode.transit_walk);
                                Route transferRoute = new GenericRouteImpl(stopFacility.getLinkId(), stop.getLinkId());
                                transferRoute.setTravelTime(0);
                                transferRoute.setDistance(0);
                                transferLeg.setRoute(transferRoute);
                                transferLeg.setTravelTime(0);

                                List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
                                tmp.addAll(routeParts);
                                tmp.add(transferLeg);
                                routeParts = tmp;
                            } else {
                                Leg transferLeg = PopulationUtils.createLeg(TransportMode.transit_walk);
                                Route transferRoute = new GenericRouteImpl(stop.getLinkId(), stopFacility.getLinkId());
                                transferRoute.setTravelTime(0);
                                transferRoute.setDistance(0);
                                transferLeg.setRoute(transferRoute);
                                transferLeg.setTravelTime(0);

                                List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
                                tmp.add(transferLeg);
                                tmp.addAll(routeParts);
                                routeParts = tmp;
                            }
                        }
                        double travelTime = getTravelTime(routeParts);
                        double marginalUtilityTraveling = this.parameters.getMarginalUtilityOfTraveling_utl_sec(mode);
                        double disutility = travelTime * -marginalUtilityTraveling;
                        InitialStop iStop = new InitialStop(stop, disutility, travelTime, routeParts);
                        initialStops.add(iStop);
                    }
                }
            }
        }
        return initialStops;
    }

    private double getTravelTime(List<? extends PlanElement> legs) {
        double travelTime = 0;
        for (PlanElement pe : legs) {
            if (pe instanceof Leg) {
                double tTime = ((Leg) pe).getTravelTime();
                if (Time.UNDEFINED_TIME != tTime) {
                    travelTime += tTime;
                }
            }
        }
        return travelTime;
    }

    private List<TransitStopFacility> findNearbyStops(Facility<?> facility) {
        double x = facility.getCoord().getX();
        double y = facility.getCoord().getY();
        Collection<TransitStopFacility> stopFacilities = this.data.stopsQT.getDisk(x, y, this.parameters.getSearchRadius());
        if (stopFacilities.size() < 2) {
            TransitStopFacility  nearestStop = this.data.stopsQT.getClosest(x, y);
            double nearestDistance = CoordUtils.calcEuclideanDistance(facility.getCoord(), nearestStop.getCoord());
            stopFacilities = this.data.stopsQT.getDisk(x, y, nearestDistance + this.parameters.getExtensionRadius());
        }
        if (stopFacilities instanceof List) {
            return (List<TransitStopFacility>) stopFacilities;
        }
        return new ArrayList<>(stopFacilities);
    }

    private RaptorRoute createDirectWalk(Facility<?> fromFacility, Facility<?> toFacility, double departureTime, Person person) {
        double beelineDistance = CoordUtils.calcEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());
        double walkTime = beelineDistance / this.parameters.getBeelineWalkSpeed();
        double walkCost_per_s = -this.parameters.getMarginalUtilityOfTravelTimeWalk_utl_s();
        double walkCost = walkTime * walkCost_per_s;

        RaptorRoute route = new RaptorRoute(fromFacility, toFacility, walkCost);
        route.addNonPt(null, null, departureTime, walkTime, beelineDistance, TransportMode.transit_walk);
        return route;
    }

    private static class ChangedLinkFacility implements Facility<TransitStopFacility> {

        private final TransitStopFacility delegate;
        private final Id<Link> linkId;

        ChangedLinkFacility(final TransitStopFacility delegate, final Id<Link> linkId) {
            this.delegate = delegate;
            this.linkId = linkId;
        }

        @Override
        public Id<Link> getLinkId() {
            return this.linkId;
        }

        @Override
        public Coord getCoord() {
            return this.delegate.getCoord();
        }

        @Override
        public Map<String, Object> getCustomAttributes() {
            return this.delegate.getCustomAttributes();
        }

        @Override
        public Id<TransitStopFacility> getId() {
            return this.delegate.getId();
        }
    }

}
