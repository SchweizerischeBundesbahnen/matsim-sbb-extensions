/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.PtConstants;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
public class SwissRailRaptorIntermodalTest {

    @Test
    public void testIntermodalTrip() {
        IntermodalFixture f = new IntermodalFixture();

        TripRouter tripRouter = new TripRouter();
        tripRouter.setRoutingModule(TransportMode.walk,
            new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3));
        tripRouter.setRoutingModule(TransportMode.bike,
            new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1500);
        bikeAccess.setFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        RaptorParameters raptorConfig = RaptorUtils.createRaptorParameters(f.config);
        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), raptorConfig, f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()), new LeastCostRaptorRouteSelector(), null, null, tripRouter);

        Facility fromFac = new FakeFacility(new Coord(10000, 10500), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(50000, 10500), Id.create("to", Link.class));

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7*3600, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals("wrong number of legs.", 5, legs.size());
        Leg leg = legs.get(0);
        Assert.assertEquals(TransportMode.bike, leg.getMode());
        Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("bike_3", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(1);
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(Id.create("bike_3", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(2);
        Assert.assertEquals(TransportMode.pt, leg.getMode());
        Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(3);
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("bike_5", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(4);
        Assert.assertEquals(TransportMode.bike, leg.getMode());
        Assert.assertEquals(Id.create("bike_5", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
    }

    @Test
    public void testIntermodalTrip_TripRouterIntegration() {
        IntermodalFixture f = new IntermodalFixture();

        RoutingModule walkRoutingModule = new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3);
        RoutingModule bikeRoutingModule = new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4);

        TripRouter tripRouter = new TripRouter();
        tripRouter.setRoutingModule(TransportMode.walk, walkRoutingModule);
        tripRouter.setRoutingModule(TransportMode.bike, bikeRoutingModule);

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1500);
        bikeAccess.setFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        RaptorParameters raptorConfig = RaptorUtils.createRaptorParameters(f.config);
        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), raptorConfig, f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()), new LeastCostRaptorRouteSelector(), null, null, tripRouter);

        RoutingModule ptRoutingModule = new SwissRailRaptorRoutingModule(raptor, f.scenario.getTransitSchedule(), f.scenario.getNetwork(), walkRoutingModule);
        tripRouter.setRoutingModule(TransportMode.pt, ptRoutingModule);

        Facility fromFac = new FakeFacility(new Coord(10000, 10500), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(50000, 10500), Id.create("to", Link.class));

        List<? extends PlanElement> planElements = tripRouter.calcRoute(TransportMode.pt, fromFac, toFac, 7*3600, f.dummyPerson);

        for (PlanElement pe : planElements) {
            System.out.println(pe);
        }

        Assert.assertEquals("wrong number of PlanElements.", 9, planElements.size());
        Assert.assertTrue(planElements.get(0) instanceof Leg);
        Assert.assertTrue(planElements.get(1) instanceof Activity);
        Assert.assertTrue(planElements.get(2) instanceof Leg);
        Assert.assertTrue(planElements.get(3) instanceof Activity);
        Assert.assertTrue(planElements.get(4) instanceof Leg);
        Assert.assertTrue(planElements.get(5) instanceof Activity);
        Assert.assertTrue(planElements.get(6) instanceof Leg);
        Assert.assertTrue(planElements.get(7) instanceof Activity);
        Assert.assertTrue(planElements.get(8) instanceof Leg);

        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(1)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(3)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(5)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(7)).getType());

        Assert.assertEquals(TransportMode.bike, ((Leg) planElements.get(0)).getMode());
        Assert.assertEquals(TransportMode.transit_walk, ((Leg) planElements.get(2)).getMode());
        Assert.assertEquals(TransportMode.pt, ((Leg) planElements.get(4)).getMode());
        Assert.assertEquals(TransportMode.transit_walk, ((Leg) planElements.get(6)).getMode());
        Assert.assertEquals(TransportMode.bike, ((Leg) planElements.get(8)).getMode());

        Assert.assertEquals(0.0, ((Activity) planElements.get(1)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(3)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(5)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(7)).getMaximumDuration(), 0.0);
    }

    @Test
    public void testIntermodalTrip_walkOnlyNoSubpop() {
        IntermodalFixture f = new IntermodalFixture();

        TripRouter tripRouter = new TripRouter();
        tripRouter.setRoutingModule(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3));
        tripRouter.setRoutingModule(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);

        RaptorParameters raptorConfig = RaptorUtils.createRaptorParameters(f.config);
        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), raptorConfig, f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()), new LeastCostRaptorRouteSelector(), null, null, tripRouter);

        Facility fromFac = new FakeFacility(new Coord(10000, 10500), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(50000, 10500), Id.create("to", Link.class));

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7*3600, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals("wrong number of legs.", 3, legs.size());
        Leg leg = legs.get(0);
        Assert.assertEquals(TransportMode.access_walk, leg.getMode());
        Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(1);
        Assert.assertEquals(TransportMode.pt, leg.getMode());
        Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(2);
        Assert.assertEquals(TransportMode.egress_walk, leg.getMode());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
    }

    /**
     * Test that if start and end are close to each other, such that the intermodal
     * access and egress go to/from the same stop, still a direct transit_walk is returned.
     */
    @Test
    public void testIntermodalTrip_withoutPt() {
        IntermodalFixture f = new IntermodalFixture();

        TripRouter tripRouter = new TripRouter();
        tripRouter.setRoutingModule(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3));
        tripRouter.setRoutingModule(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1500);
        bikeAccess.setFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        RaptorParameters raptorConfig = RaptorUtils.createRaptorParameters(f.config);
        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), raptorConfig, f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()), new LeastCostRaptorRouteSelector(), null, null, tripRouter);

        Facility fromFac = new FakeFacility(new Coord(8000, 10500), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(13000, 10500), Id.create("to", Link.class));

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7*3600, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals("wrong number of legs.", 1, legs.size());
        Leg leg = legs.get(0);
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(5000, leg.getRoute().getDistance(), 0.0);
    }

    /**
     * Provides a simple scenario with a single transit line connecting two
     * areas (one around coordinates 10000/10000, the other around 50000/10000).
     * In each area, several transit stops with different characteristics are
     * located along the transit line:
     *
     * <pre>
     * [n]  stop facility
     * ---  link, transit route
     *
     *
     * [0]---[1]---[2]---[3]---------------------[4]---[5]---[6]---[7]
     *  B                 B                             B           B
     *        H           H                             H
     *
     *  B: bikeAccessible=true
     *  H: hub=true
     *
     * </pre>
     *
     * The stops have the following attributes:
     * <ul>
     *     <li>0: bikeAccessible=true   hub=false</li>
     *     <li>1: bikeAccessible=false  hub=true</li>
     *     <li>2: bikeAccessible=false  hub=false</li>
     *     <li>3: bikeAccessible=true   hub=true</li>
     *     <li>4: bikeAccessible=false  hub=false</li>
     *     <li>5: bikeAccessible=true   hub=true</li>
     *     <li>6: (none)</li>
     *     <li>7: bikeAccessible=true</li>
     * </ul>
     *
     * The line is running every 10 minutes between 06:00 and 08:00 from [0] to [7].
     *
     *
     * @author mrieser / SBB
     */
    private static class IntermodalFixture {

        private final SwissRailRaptorConfigGroup srrConfig;
        private final Config config;
        private final Scenario scenario;
        private final Person dummyPerson;

        IntermodalFixture() {
            this.srrConfig = new SwissRailRaptorConfigGroup();
            this.config = ConfigUtils.createConfig(this.srrConfig);
            this.scenario = ScenarioUtils.createScenario(this.config);

            TransitSchedule schedule = this.scenario.getTransitSchedule();
            TransitScheduleFactory sf = schedule.getFactory();

            Id<Link>[] ptLinkIds = new Id[8];
            for (int i = 0; i < ptLinkIds.length; i++) {
                ptLinkIds[i] = Id.create("pt_" + i, Link.class);
            }

            TransitStopFacility[] stops = new TransitStopFacility[8];
            stops[0] = sf.createTransitStopFacility(Id.create(0, TransitStopFacility.class), new Coord( 9000, 10000), false);
            stops[1] = sf.createTransitStopFacility(Id.create(1, TransitStopFacility.class), new Coord( 9500, 10000), false);
            stops[2] = sf.createTransitStopFacility(Id.create(2, TransitStopFacility.class), new Coord(10000, 10000), false);
            stops[3] = sf.createTransitStopFacility(Id.create(3, TransitStopFacility.class), new Coord(10500, 10000), false);
            stops[4] = sf.createTransitStopFacility(Id.create(4, TransitStopFacility.class), new Coord(49500, 10000), false);
            stops[5] = sf.createTransitStopFacility(Id.create(5, TransitStopFacility.class), new Coord(50000, 10000), false);
            stops[6] = sf.createTransitStopFacility(Id.create(6, TransitStopFacility.class), new Coord(50500, 10000), false);
            stops[7] = sf.createTransitStopFacility(Id.create(7, TransitStopFacility.class), new Coord(51000, 10000), false);

            for (int i = 0; i < stops.length; i++) {
                TransitStopFacility stop = stops[i];
                stop.setLinkId(ptLinkIds[i]);
                schedule.addStopFacility(stop);
            }

            stops[0].getAttributes().putAttribute("bikeAccessible", "false");
            stops[1].getAttributes().putAttribute("bikeAccessible", "true");
            stops[2].getAttributes().putAttribute("bikeAccessible", "false");
            stops[3].getAttributes().putAttribute("bikeAccessible", "true");
            stops[4].getAttributes().putAttribute("bikeAccessible", "false");
            stops[5].getAttributes().putAttribute("bikeAccessible", "true");
            stops[7].getAttributes().putAttribute("bikeAccessible", "true");

            stops[0].getAttributes().putAttribute("hub", false);
            stops[1].getAttributes().putAttribute("hub", true);
            stops[2].getAttributes().putAttribute("hub", false);
            stops[3].getAttributes().putAttribute("hub", true);
            stops[4].getAttributes().putAttribute("hub", false);
            stops[5].getAttributes().putAttribute("hub", true);

            for (int i = 0; i < stops.length; i++) {
                if ("true".equals(stops[i].getAttributes().getAttribute("bikeAccessible"))) {
                    stops[i].getAttributes().putAttribute("accessLinkId_bike", "bike_" + i);
                }
            }

            TransitLine line = sf.createTransitLine(Id.create("oneway", TransitLine.class));

            List<TransitRouteStop> rStops = new ArrayList<>();
            for (int i = 0; i < stops.length; i++) {
                double arrivalTime = i * 120 + (i > 3 ? 1200 : 0);
                double departureTime = arrivalTime + 60;
                rStops.add(sf.createTransitRouteStop(stops[i], arrivalTime, departureTime));
            }
            LinkNetworkRouteImpl networkRoute = new LinkNetworkRouteImpl(ptLinkIds[0], Arrays.copyOfRange(ptLinkIds, 1, 7), ptLinkIds[7]);
            TransitRoute route = sf.createTransitRoute(Id.create("goEast", TransitRoute.class), networkRoute, rStops, "train");

            for (int i = 0; i < 13; i++) {
                Departure d = sf.createDeparture(Id.create(i, Departure.class), 6*3600 + i * 600);
                route.addDeparture(d);
            }

            line.addRoute(route);
            schedule.addTransitLine(line);

            this.dummyPerson = this.scenario.getPopulation().getFactory().createPerson(Id.create("dummy", Person.class));

            Network network = this.scenario.getNetwork();
            NetworkFactory nf = network.getFactory();
            Node[] nodes = new Node[stops.length + 1];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = nf.createNode(Id.create(i, Node.class), new Coord(10000 + 5000 * i, 10000));
                network.addNode(nodes[i]);
            }
            for (int i = 0; i < stops.length; i++) {
                Link link = nf.createLink(Id.create("pt_" + i, Link.class), nodes[i], nodes[i+1]);
                network.addLink(link);
                link = nf.createLink(Id.create("bike_" + i, Link.class), nodes[i], nodes[i+1]);
                network.addLink(link);
            }

        }
    }

    public static final class FakeFacility implements Facility {
        private final Coord coord;
        private final Id<Link> linkId;

        FakeFacility(Coord coord, Id<Link> linkId) {
            this.coord = coord;
            this.linkId = linkId;
        }

        public Coord getCoord() {
            return this.coord;
        }

        public Id getId() {
            throw new RuntimeException("not implemented");
        }

        public Map<String, Object> getCustomAttributes() {
            throw new RuntimeException("not implemented");
        }

        public Id getLinkId() {
            return this.linkId;
        }
    }

}
