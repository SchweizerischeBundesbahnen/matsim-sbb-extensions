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
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final RaptorStaticConfig config;
    private final RaptorParametersForPerson parametersForPerson;
    private final RaptorRouteSelector defaultRouteSelector;
    private final RaptorIntermodalAccessEgress intermodalAE;
    private final String subpopulationAttribute;
    private final ObjectAttributes personAttributes;
    private final Map<String, RoutingModule> routingModules;

    private boolean treeWarningShown = false;

    public SwissRailRaptor(final SwissRailRaptorData data, RaptorParametersForPerson parametersForPerson,
                           RaptorRouteSelector routeSelector, RaptorIntermodalAccessEgress intermodalAE) {
        this(data, parametersForPerson, routeSelector, intermodalAE, null, null, null);
        log.info("SwissRailRaptor was initialized without support for subpopulations or intermodal access/egress legs.");
    }

    public SwissRailRaptor(final SwissRailRaptorData data, RaptorParametersForPerson parametersForPerson,
                           RaptorRouteSelector routeSelector, RaptorIntermodalAccessEgress intermodalAE,
                           String subpopulationAttribute, ObjectAttributes personAttributes, Map<String, RoutingModule> routingModules) {
        this.data = data;
        this.config = data.config;
        this.raptor = new SwissRailRaptorCore(data);
        this.parametersForPerson = parametersForPerson;
        this.defaultRouteSelector = routeSelector;
        this.intermodalAE = intermodalAE;
        this.subpopulationAttribute = subpopulationAttribute;
        this.personAttributes = personAttributes;
        this.routingModules = routingModules;
    }

    @Override
    public List<Leg> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double departureTime, Person person) {
        RaptorParameters parameters = this.parametersForPerson.getRaptorParameters(person);
        if (parameters.getConfig().isUseRangeQuery()) {
            return this.performRangeQuery(fromFacility, toFacility, departureTime, person, parameters);
        }
        List<InitialStop> accessStops = findAccessStops(fromFacility, person, departureTime, parameters);
        List<InitialStop> egressStops = findEgressStops(toFacility, person, departureTime, parameters);

        RaptorRoute foundRoute = this.raptor.calcLeastCostRoute(departureTime, fromFacility, toFacility, accessStops, egressStops, parameters);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, departureTime, person);

        if (foundRoute == null || directWalk.getTotalCosts() < foundRoute.getTotalCosts()) {
            foundRoute = directWalk;
        }
        List<Leg> legs = RaptorUtils.convertRouteToLegs(foundRoute);
        return legs;
    }

    private List<Leg> performRangeQuery(Facility<?> fromFacility, Facility<?> toFacility, double desiredDepartureTime, Person person, RaptorParameters parameters) {
        SwissRailRaptorConfigGroup srrConfig = parameters.getConfig();

        Object attr = this.personAttributes.getAttribute(person.getId().toString(), this.subpopulationAttribute);
        String subpopulation = attr == null ? null : attr.toString();
        SwissRailRaptorConfigGroup.RangeQuerySettingsParameterSet rangeSettings = srrConfig.getRangeQuerySettings(subpopulation);

        double earliestDepartureTime = desiredDepartureTime - rangeSettings.getMaxEarlierDeparture();
        double latestDepartureTime = desiredDepartureTime + rangeSettings.getMaxLaterDeparture();

        if (this.defaultRouteSelector instanceof ConfigurableRaptorRouteSelector) {
            ConfigurableRaptorRouteSelector selector = (ConfigurableRaptorRouteSelector) this.defaultRouteSelector;

            SwissRailRaptorConfigGroup.RouteSelectorParameterSet params = srrConfig.getRouteSelector(subpopulation);

            selector.setBetaTransfer(params.getBetaTransfers());
            selector.setBetaTravelTime(params.getBetaTravelTime());
            selector.setBetaDepartureTime(params.getBetaDepartureTime());
        }

        return this.calcRoute(fromFacility, toFacility, earliestDepartureTime, desiredDepartureTime, latestDepartureTime, person, this.defaultRouteSelector);
    }

    public List<Leg> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double earliestDepartureTime, double desiredDepartureTime, double latestDepartureTime, Person person) {
        return calcRoute(fromFacility, toFacility, earliestDepartureTime, desiredDepartureTime, latestDepartureTime, person, this.defaultRouteSelector);
    }

    public List<Leg> calcRoute(Facility<?> fromFacility, Facility<?> toFacility, double earliestDepartureTime, double desiredDepartureTime, double latestDepartureTime, Person person, RaptorRouteSelector selector) {
        RaptorParameters parameters = this.parametersForPerson.getRaptorParameters(person);
        List<InitialStop> accessStops = findAccessStops(fromFacility, person, desiredDepartureTime, parameters);
        List<InitialStop> egressStops = findEgressStops(toFacility, person, desiredDepartureTime, parameters);

        List<RaptorRoute> foundRoutes = this.raptor.calcRoutes(earliestDepartureTime, desiredDepartureTime, latestDepartureTime, fromFacility, toFacility, accessStops, egressStops, parameters);
        RaptorRoute foundRoute = selector.selectOne(foundRoutes, desiredDepartureTime);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, desiredDepartureTime, person);

        if (foundRoute == null || directWalk.getTotalCosts() < foundRoute.getTotalCosts()) {
            foundRoute = directWalk;
        }
        List<Leg> legs = RaptorUtils.convertRouteToLegs(foundRoute);
        // TODO adapt the activity end time of the activity right before this trip
        /* Sadly, it's not that easy to find the previous activity, as we only have from- and to-facility
         * and the departure time. One would have to search through the person's selectedPlan to find
         * a matching activity, but what if an agent travels twice a day between from- and to-activity
         * and it only sets the activity duration, but not the end-time?
         * One could try to come up with some heuristic, but that would be very error-prone and
         * not satisfying. The clean solution would be to implement our own PlanRouter which
         * uses our own TripRouter which would take care of adapting the departure time,
         * but sadly PlanRouter is hardcoded in several places (e.g. PrepareForSimImpl), so it
         * cannot easily be replaced. So I fear I currently don't see a simple solution for that.
         * mrieser / march 2018.
         */
        return legs;
    }

    public List<RaptorRoute> calcRoutes(Facility<?> fromFacility, Facility<?> toFacility, double earliestDepartureTime, double desiredDepartureTime, double latestDepartureTime, Person person) {
        RaptorParameters parameters = this.parametersForPerson.getRaptorParameters(person);
        List<InitialStop> accessStops = findAccessStops(fromFacility, person, desiredDepartureTime, parameters);
        List<InitialStop> egressStops = findEgressStops(toFacility, person, desiredDepartureTime, parameters);

        List<RaptorRoute> foundRoutes = this.raptor.calcRoutes(earliestDepartureTime, desiredDepartureTime, latestDepartureTime, fromFacility, toFacility, accessStops, egressStops, parameters);
        RaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, desiredDepartureTime, person);

        if (foundRoutes == null) {
            foundRoutes = new ArrayList<>(1);
        }
        if (foundRoutes.isEmpty() || directWalk.getTotalCosts() < foundRoutes.get(0).getTotalCosts()) {
            foundRoutes.add(directWalk); // add direct walk if it seems plausible
        }
        return foundRoutes;
    }

    public Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> calcTree(TransitStopFacility fromStop, double departureTime, RaptorParameters parameters) {
        return this.calcTree(Collections.singletonList(fromStop), departureTime, parameters);
    }

    public Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> calcTree(Collection<TransitStopFacility> fromStops, double departureTime, RaptorParameters parameters) {
        if (this.data.config.getOptimization() != RaptorStaticConfig.RaptorOptimization.OneToAllRouting && !this.treeWarningShown) {
            log.warn("SwissRailRaptorData was not initialized with full support for tree calculations and may result in unexpected results. Use `RaptorStaticConfig.setOptimization(RaptorOptimization.OneToAllRouting)` to fix this issue.");
            this.treeWarningShown = true;
        }
        List<InitialStop> accessStops = new ArrayList<>();
        for (TransitStopFacility stop : fromStops) {
            accessStops.add(new InitialStop(stop, 0, 0, 0, null));
        }
        return this.calcLeastCostTree(accessStops, departureTime, parameters);
    }

    public Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> calcTree(Facility<?> fromFacility, double departureTime, Person person) {
        RaptorParameters parameters = this.parametersForPerson.getRaptorParameters(person);
        List<InitialStop> accessStops = findAccessStops(fromFacility, person, departureTime, parameters);
        return this.calcLeastCostTree(accessStops, departureTime, parameters);
    }

    private Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> calcLeastCostTree(Collection<InitialStop> accessStops, double departureTime, RaptorParameters parameters) {
        return this.raptor.calcLeastCostTree(departureTime, accessStops, parameters);
    }

    public SwissRailRaptorData getUnderlyingData() {
        return this.data;
    }

    private List<InitialStop> findAccessStops(Facility<?> facility, Person person, double departureTime, RaptorParameters parameters) {
        SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
        if (srrCfg.isUseIntermodalAccessEgress()) {
            return findIntermodalStops(facility, person, departureTime, Direction.Access, parameters);
        } else {
            List<TransitStopFacility> stops = findNearbyStops(facility, parameters);
            List<InitialStop> initialStops = stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / this.config.getBeelineWalkSpeed());
                double disutility = travelTime * -this.config.getMarginalUtilityOfTravelTimeAccessWalk_utl_s();
                return new InitialStop(stop, disutility, travelTime, beelineDistance, TransportMode.access_walk);
            }).collect(Collectors.toList());
            return initialStops;
        }
    }

    private List<InitialStop> findEgressStops(Facility<?> facility, Person person, double departureTime, RaptorParameters parameters) {
        SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
        if (srrCfg.isUseIntermodalAccessEgress()) {
            return findIntermodalStops(facility, person, departureTime, Direction.Egress, parameters);
        } else {
            List<TransitStopFacility> stops = findNearbyStops(facility, parameters);
            List<InitialStop> initialStops = stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / this.config.getBeelineWalkSpeed());
                double disutility = travelTime * -this.config.getMarginalUtilityOfTravelTimeEgressWalk_utl_s();
                return new InitialStop(stop, disutility, travelTime, beelineDistance, TransportMode.egress_walk);
            }).collect(Collectors.toList());
            return initialStops;
        }
    }

    private enum Direction { Access, Egress }

    private List<InitialStop> findIntermodalStops(Facility<?> facility, Person person, double departureTime, Direction direction, RaptorParameters parameters) {
        SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
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
            String linkIdAttribute = paramset.getLinkIdAttribute();
            String personFilterAttribute = paramset.getPersonFilterAttribute();
            String personFilterValue = paramset.getPersonFilterValue();
            String stopFilterAttribute = paramset.getStopFilterAttribute();
            String stopFilterValue = paramset.getStopFilterValue();

            boolean personMatches = true;
            if (personFilterAttribute != null) {
                Object attr = this.personAttributes.getAttribute(personId, personFilterAttribute);
                String attrValue = attr == null ? null : attr.toString();
                personMatches = personFilterValue.equals(attrValue);
            }

            if (personMatches) {
                Collection<TransitStopFacility> stopFacilities = this.data.stopsQT.getDisk(x, y, radius);
                for (TransitStopFacility stop : stopFacilities) {
                    boolean filterMatches = true;
                    if (stopFilterAttribute != null) {
                        Object attr = stop.getAttributes().getAttribute(stopFilterAttribute);
                        String attrValue = attr == null ? null : attr.toString();
                        filterMatches = stopFilterValue.equals(attrValue);
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
                            RoutingModule module = this.routingModules.get(mode);
                            routeParts = module.calcRoute(facility, stopFacility, departureTime, person);
                        } else { // it's Egress
                            // We don't know the departure time for the egress trip, so just use the original departureTime,
                            // although it is wrong and might result in a wrong traveltime and thus wrong route.
                            RoutingModule module = this.routingModules.get(mode);
                            routeParts = module.calcRoute(stopFacility, facility, departureTime, person);
                            // clear the (wrong) departureTime so users don't get confused
                            for (PlanElement pe : routeParts) {
                                if (pe instanceof Leg) {
                                    ((Leg) pe).setDepartureTime(Time.getUndefinedTime());
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
                                Route transferRoute = RouteUtils.createGenericRouteImpl(stopFacility.getLinkId(), stop.getLinkId());
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
                                Route transferRoute = RouteUtils.createGenericRouteImpl(stop.getLinkId(), stopFacility.getLinkId());
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
                        RaptorIntermodalAccessEgress.RIntermodalAccessEgress accessEgress = this.intermodalAE.calcIntermodalAccessEgress(routeParts, parameters);
                        InitialStop iStop = new InitialStop(stop, accessEgress.disutility, accessEgress.travelTime, accessEgress.routeParts);
                        initialStops.add(iStop);
                    }
                }
            }
        }
        return initialStops;
    }

    private List<TransitStopFacility> findNearbyStops(Facility<?> facility, RaptorParameters parameters) {
        double x = facility.getCoord().getX();
        double y = facility.getCoord().getY();
        Collection<TransitStopFacility> stopFacilities = this.data.stopsQT.getDisk(x, y, parameters.getSearchRadius());
        if (stopFacilities.size() < 2) {
            TransitStopFacility  nearestStop = this.data.stopsQT.getClosest(x, y);
            double nearestDistance = CoordUtils.calcEuclideanDistance(facility.getCoord(), nearestStop.getCoord());
            stopFacilities = this.data.stopsQT.getDisk(x, y, nearestDistance + parameters.getExtensionRadius());
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
