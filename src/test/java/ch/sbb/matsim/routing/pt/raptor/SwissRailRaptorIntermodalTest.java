/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.facilities.Facility;
import org.matsim.pt.PtConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
public class SwissRailRaptorIntermodalTest {

    @Test
    public void testIntermodalTrip() {
        IntermodalFixture f = new IntermodalFixture();

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
            new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3));
        routingModules.put(TransportMode.bike,
            new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1500);
        bikeAccess.setStopFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setStopFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, routingModules);

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

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk, walkRoutingModule);
        routingModules.put(TransportMode.bike, bikeRoutingModule);

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
        bikeAccess.setStopFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setStopFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, routingModules);

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

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(-8.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(-8.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, routingModules);

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
        Assert.assertEquals(Id.create("pt_2", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(1);
        Assert.assertEquals(TransportMode.pt, leg.getMode());
        Assert.assertEquals(Id.create("pt_2", Link.class), leg.getRoute().getStartLinkId());
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

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1200);
        bikeAccess.setStopFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setStopFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, routingModules);

        Facility fromFac = new FakeFacility(new Coord(10000, 9000), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(11000, 11000), Id.create("to", Link.class));

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7*3600, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals("wrong number of legs.", 1, legs.size());
        Leg leg = legs.get(0);
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(Math.sqrt(1000*1000+2000*2000), leg.getRoute().getDistance(), 1e-7);
    }

    @Test
    public void testIntermodalTrip_competingAccess() {
        IntermodalFixture f = new IntermodalFixture();

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario.getPopulation().getFactory(), 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 3, 1.4));

        // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
        // which would result in all options having the same cost in the end.
        f.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
        f.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(egressWalk);

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(100); // force to nearest stops
        f.srrConfig.addIntermodalAccessEgress(walkAccess);

        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(100); // force to nearest stops
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        Facility fromFac = new FakeFacility(new Coord(10500, 10050), Id.create("from", Link.class)); // stop 3
        Facility toFac = new FakeFacility(new Coord(50000, 10050), Id.create("to", Link.class)); // stop 5

        // first check: bike should be the better option
        {
            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, routingModules);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
        }

        // second check: decrease bike speed, walk should be the better option
        // do the test this way to insure it is not accidentally correct due to the accidentally correct order the modes are initialized
        {
            routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario.getPopulation().getFactory(), 1.0, 1.4));

            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, routingModules);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
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
    }

}
