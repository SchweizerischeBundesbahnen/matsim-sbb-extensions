/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests for the tree-calculating functionality of SwissRailRaptor
 *
 * @author mrieser / SBB
 */
public class SwissRailRaptorTreeTest {

    @Test
    public void testSingleStop_dep0740atN_optimized() {
        Fixture f = new Fixture();
        f.init();

        RaptorStaticConfig config = RaptorUtils.createStaticConfig(f.config);
        config.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), config, f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, null);

        RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);

        // start with a stop on the green line
        TransitStopFacility fromStop = f.schedule.getFacilities().get(Id.create(23, TransitStopFacility.class));
        double depTime = 7*3600 + 40*60;
        Map<Id<TransitStopFacility>, TravelInfo> map = raptor.calcTree(fromStop, depTime, raptorParams);

        Assert.assertEquals("wrong number of reached stops.", f.schedule.getFacilities().size(), map.size());

        assertTravelInfo(map, 0 , 1, "08:14:06"); // transfer at C, 7:50/8:02 blue, walk at A
        assertTravelInfo(map, 1 , 1, "08:14:00"); // transfer at C, 7:50/8:02 blue
        assertTravelInfo(map, 2 , 1, "08:09:06"); // transfer at C, 7:50/8:02 blue, walk at B
        assertTravelInfo(map, 3 , 1, "08:09:00"); // transfer at C, 7:50/8:02 blue
        assertTravelInfo(map, 4 , 0, "07:50:03"); // transfer at C, 7:50, walk 3 seconds (2 meters)
        assertTravelInfo(map, 5 , 0, "07:50:03"); // transfer at C, 7:50, walk 3 seconds (2 meters)
        assertTravelInfo(map, 6 , 1, "08:09:00"); // transfer at C, 7:50/8:02 blue
        assertTravelInfo(map, 7 , 1, "08:09:06"); // transfer at C, 7:50/8:02 blue, walk at D
        assertTravelInfo(map, 8 , 1, "08:16:00"); // transfer at C, 7:50/8:02 blue
        assertTravelInfo(map, 9 , 1, "08:16:06"); // transfer at C, 7:50/8:02 blue, walk at E
        assertTravelInfo(map, 10, 1, "08:23:00"); // transfer at C, 7:50/8:02 blue (travelling to 11 and transferring would be faster, but not cheaper!
        assertTravelInfo(map, 11, 2, "08:19:00"); // transfer at C, 7:50/8:00 red, transfer at G, 8:09/8:12
        assertTravelInfo(map, 12, 1, "08:09:00"); // transfer at C, 7:50/8:00 red
        assertTravelInfo(map, 13, 1, "08:09:06"); // transfer at C, 7:50/8:00 red, walk 4 meters / 6 seconds
        assertTravelInfo(map, 14, 2, "08:19:00"); // transfer at C, 7:50/8:00 red, transfer at G, 8:09/8:12
        assertTravelInfo(map, 15, 2, "08:19:06"); // same as [14], then walk
        assertTravelInfo(map, 16, 2, "08:24:00"); // transfer at C, 7:50/8:00 red, transfer at G, 8:09/8:12
        assertTravelInfo(map, 17, 2, "08:24:06"); // same as [16], then walk
        assertTravelInfo(map, 18, 0, "07:50:00"); // directly reachable
        assertTravelInfo(map, 19, 1, "08:01:00"); // transfer at C, 7:50/7:51 green
        assertTravelInfo(map, 20, 1, "08:11:00"); // transfer at C, 7:50/7:51 green
        assertTravelInfo(map, 21, 1, "08:09:03"); // transfer at C, 7:50/8:00 red, walk 2 meters / 3 seconds
        assertTravelInfo(map, 22, 2, "08:21:00"); // transfer at C, 7:50/8:00 red, transfer at G 8:09/8:11
        assertTravelInfo(map, 23, 0, "07:40:00"); // our start location
    }

    @Test
    public void testSingleStop_dep0740atN_unoptimized() {
        Fixture f = new Fixture();
        f.init();

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, null);

        RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);

        // start with a stop on the green line
        TransitStopFacility fromStop = f.schedule.getFacilities().get(Id.create(23, TransitStopFacility.class));
        double depTime = 7*3600 + 40*60;
        Map<Id<TransitStopFacility>, TravelInfo> map = raptor.calcTree(fromStop, depTime, raptorParams);

        Assert.assertEquals("wrong number of reached stops.", 20, map.size());

        Assert.assertNull(map.get(Id.create(0, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 1 , 1, "08:14:00"); // transfer at C, 7:50/8:02 blue
        Assert.assertNull(map.get(Id.create(2, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 3 , 1, "08:09:00"); // transfer at C, 7:50/8:02 blue
        assertTravelInfo(map, 4 , 0, "07:50:03"); // transfer at C, 7:50, walk 3 seconds (2 meters)
        assertTravelInfo(map, 5 , 0, "07:50:03"); // transfer at C, 7:50, walk 3 seconds (2 meters)
        assertTravelInfo(map, 6 , 1, "08:09:00"); // transfer at C, 7:50/8:02 blue
        assertTravelInfo(map, 7 , 2, "08:33:00"); // transfer at C, 7:50/8:00 red, transfer at G 8:09/8.12
        assertTravelInfo(map, 8 , 1, "08:16:00"); // transfer at C, 7:50/8:02 blue
        assertTravelInfo(map, 9 , 2, "08:26:00"); // transfer at C, 7:50/8:00 red, transfer at G 8:09/8.12
        assertTravelInfo(map, 10, 1, "08:23:00"); // transfer at C, 7:50/8:02 blue (travelling to 11 and transferring would be faster, but not cheaper!
        assertTravelInfo(map, 11, 2, "08:19:00"); // transfer at C, 7:50/8:00 red, transfer at G, 8:09/8:12
        assertTravelInfo(map, 12, 1, "08:09:00"); // transfer at C, 7:50/8:00 red
        assertTravelInfo(map, 13, 1, "08:09:06"); // transfer at C, 7:50/8:00 red, walk 4 meters / 6 seconds
        assertTravelInfo(map, 14, 2, "08:19:00"); // transfer at C, 7:50/8:00 red, transfer at G, 8:09/8:12
        Assert.assertNull(map.get(Id.create(15, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 16, 2, "08:24:00"); // transfer at C, 7:50/8:00 red, transfer at G, 8:09/8:12
        Assert.assertNull(map.get(Id.create(17, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 18, 0, "07:50:00"); // directly reachable
        assertTravelInfo(map, 19, 1, "08:01:00"); // transfer at C, 7:50/7:51 green
        assertTravelInfo(map, 20, 1, "08:11:00"); // transfer at C, 7:50/7:51 green
        assertTravelInfo(map, 21, 1, "08:09:03"); // transfer at C, 7:50/8:00 red, walk 2 meters / 3 seconds
        assertTravelInfo(map, 22, 2, "08:21:00"); // transfer at C, 7:50/8:00 red, transfer at G 8:09/8:11
        assertTravelInfo(map, 23, 0, "07:40:00"); // our start location
    }

    @Test
    public void testSingleStop_dep0750atN_optimized() {
        Fixture f = new Fixture();
        f.init();

        RaptorStaticConfig config = RaptorUtils.createStaticConfig(f.config);
        config.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), config, f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, null);

        RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);

        // start with a stop on the green line
        TransitStopFacility fromStop = f.schedule.getFacilities().get(Id.create(23, TransitStopFacility.class));
        double depTime = 7*3600 + 50*60;
        Map<Id<TransitStopFacility>, TravelInfo> map = raptor.calcTree(fromStop, depTime, raptorParams);

        // latest departure on green line is at 07:51, so we'll miss some stops!
        Assert.assertEquals("wrong number of reached stops.", 21, map.size());

        assertTravelInfo(map, 0 , 1, "08:14:06"); // same as [1], then walk
        assertTravelInfo(map, 1 , 1, "08:14:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 2 , 1, "08:09:06"); // same as [3], then walk
        assertTravelInfo(map, 3 , 1, "08:09:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 4 , 0, "08:00:03"); // transfer at C, 8:00, walk 3 seconds (2 meters)
        assertTravelInfo(map, 5 , 0, "08:00:03"); // transfer at C, 8:00, walk 3 seconds (2 meters)
        assertTravelInfo(map, 6 , 1, "08:09:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 7 , 1, "08:09:06"); // same as [6], then walk
        assertTravelInfo(map, 8 , 1, "08:16:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 9 , 1, "08:16:06"); // same as [8], then walk
        assertTravelInfo(map, 10, 1, "08:23:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 11, 1, "08:23:06"); // same as [10], then walk
        assertTravelInfo(map, 12, 1, "08:28:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 13, 1, "08:28:06"); // transfer at C, 8:00/8:02 blue, walk (4 meters) (transfer to red line is allowed)
        assertTravelInfo(map, 14, 1, "08:39:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 15, 1, "08:39:06"); // same as [14], then walk
        assertTravelInfo(map, 16, 1, "08:44:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 17, 1, "08:44:06"); // same as [16], then walk
        assertTravelInfo(map, 18, 0, "08:00:00"); // directly reachable
        Assert.assertNull(map.get(Id.create(19, TransitStopFacility.class))); // unreachable
        Assert.assertNull(map.get(Id.create(20, TransitStopFacility.class)));
        assertTravelInfo(map, 21, 1, "08:28:03"); // transfer at C, 8:00/8:01 green, walk 2 meters / 3 seconds
        Assert.assertNull(map.get(Id.create(22, TransitStopFacility.class)));
        assertTravelInfo(map, 23, 0, "07:50:00"); // our start location
    }

    @Test
    public void testSingleStop_dep0750atN_unoptimized() {
        Fixture f = new Fixture();
        f.init();

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, null);

        RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);

        // start with a stop on the green line
        TransitStopFacility fromStop = f.schedule.getFacilities().get(Id.create(23, TransitStopFacility.class));
        double depTime = 7*3600 + 50*60;
        Map<Id<TransitStopFacility>, TravelInfo> map = raptor.calcTree(fromStop, depTime, raptorParams);

        // latest departure on green line is at 07:51, so we'll miss some stops!
        Assert.assertEquals("wrong number of reached stops.", 14, map.size());

        Assert.assertNull(map.get(Id.create(0, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 1 , 1, "08:14:00"); // transfer at C, 8:00/8:02 blue
        Assert.assertNull(map.get(Id.create(2, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 3 , 1, "08:09:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 4 , 0, "08:00:03"); // transfer at C, 8:00, walk 3 seconds (2 meters)
        assertTravelInfo(map, 5 , 0, "08:00:03"); // transfer at C, 8:00, walk 3 seconds (2 meters)
        assertTravelInfo(map, 6 , 1, "08:09:00"); // transfer at C, 8:00/8:02 blue
        Assert.assertNull(map.get(Id.create(7, TransitStopFacility.class))); // unreachable, no more departures at C(red) or G(blue)
        assertTravelInfo(map, 8 , 1, "08:16:00"); // transfer at C, 8:00/8:02 blue
        Assert.assertNull(map.get(Id.create(9, TransitStopFacility.class))); // unreachable, no more departures at C(red) or G(blue)
        assertTravelInfo(map, 10, 1, "08:23:00"); // transfer at C, 8:00/8:02 blue
        Assert.assertNull(map.get(Id.create(11, TransitStopFacility.class))); // unreachable, no more departures at C(red) or G(blue)
        assertTravelInfo(map, 12, 1, "08:28:00"); // transfer at C, 8:00/8:02 blue
        assertTravelInfo(map, 13, 1, "08:28:06"); // transfer at C, 8:00/8:02 blue, walk (4 meters) (transfer to red line is allowed)
        assertTravelInfo(map, 14, 1, "08:39:00"); // transfer at C, 8:00/8:02 blue
        Assert.assertNull(map.get(Id.create(15, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 16, 1, "08:44:00"); // transfer at C, 8:00/8:02 blue
        Assert.assertNull(map.get(Id.create(17, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 18, 0, "08:00:00"); // directly reachable
        Assert.assertNull(map.get(Id.create(19, TransitStopFacility.class))); // unreachable
        Assert.assertNull(map.get(Id.create(20, TransitStopFacility.class)));
        assertTravelInfo(map, 21, 1, "08:28:03"); // transfer at C, 8:00/8:01 green, walk 2 meters / 3 seconds
        Assert.assertNull(map.get(Id.create(22, TransitStopFacility.class)));
        assertTravelInfo(map, 23, 0, "07:50:00"); // our start location
    }

    @Test
    public void testMultipleStops_optimized() {
        Fixture f = new Fixture();
        f.init();

        RaptorStaticConfig config = RaptorUtils.createStaticConfig(f.config);
        config.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), config, f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, null);

        RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);

        // start at B and H
        TransitStopFacility fromStopB = f.schedule.getFacilities().get(Id.create(2, TransitStopFacility.class));
        TransitStopFacility fromStopH = f.schedule.getFacilities().get(Id.create(15, TransitStopFacility.class));
        double depTime = 7*3600 + 30*60;
        List<TransitStopFacility> fromStops = new ArrayList<>();
        fromStops.add(fromStopB);
        fromStops.add(fromStopH);
        Map<Id<TransitStopFacility>, TravelInfo> map = raptor.calcTree(fromStops, depTime, raptorParams);

        Assert.assertEquals("wrong number of reached stops.", f.schedule.getFacilities().size(), map.size());

        assertTravelInfo(map, 0 , 1, "07:54:06"); // transfer at B, walk at A
        assertTravelInfo(map, 1 , 1, "07:54:00"); // transfer at B
        assertTravelInfo(map, 2 , 0, "07:30:00"); // from B, we started here
        assertTravelInfo(map, 3 , 0, "07:30:06"); // from B, walk
        assertTravelInfo(map, 4 , 0, "07:38:00"); // from B, directly reachable
        assertTravelInfo(map, 5 , 0, "07:38:06"); // same as [4], then walk
        assertTravelInfo(map, 6 , 0, "07:49:00"); // from B, directly reachable
        assertTravelInfo(map, 7 , 0, "07:49:06"); // same as [6], then walk
        assertTravelInfo(map, 8 , 0, "07:56:00"); // from B, directly reachable
        assertTravelInfo(map, 9 , 0, "07:56:06"); // same as [8], then walk
        assertTravelInfo(map, 10, 0, "08:03:00"); // from B, directly reachable (would be faster from H, but not cheaper due to the transfer penalty at the end)
        assertTravelInfo(map, 11, 0, "07:59:00"); // from H, directly reachable
        assertTravelInfo(map, 12, 0, "07:48:06"); // same as [13], then walk
        assertTravelInfo(map, 13, 0, "07:48:00"); // from H, directly reachable
        assertTravelInfo(map, 14, 0, "07:30:06"); // same as [15], then walk
        assertTravelInfo(map, 15, 0, "07:30:00"); // from H, we started here
        assertTravelInfo(map, 16, 0, "07:44:00"); // from H[14]
        assertTravelInfo(map, 17, 0, "07:44:06"); // same as [16], then walk
        assertTravelInfo(map, 18, 0, "07:38:03"); // same as [2], then walk
        assertTravelInfo(map, 19, 1, "07:51:00"); // from B, transfer at C, 7:38/7:41 green
        assertTravelInfo(map, 20, 1, "08:01:00"); // from B, transfer at C, 7:38/7:41 green
        assertTravelInfo(map, 21, 0, "07:48:03"); // from H, transfer at G (walk)
        assertTravelInfo(map, 22, 1, "08:01:00"); // from H, transfer at G, 7:48/7:51 green
        assertTravelInfo(map, 23, 1, "08:11:00"); // from H, transfer at G, 7:48/7:51 green
    }

    @Test
    public void testMultipleStops_unoptimized() {
        Fixture f = new Fixture();
        f.init();

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), new DefaultRaptorIntermodalAccessEgress(), null, null, null);

        RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);

        // start at B and H
        TransitStopFacility fromStopB = f.schedule.getFacilities().get(Id.create(2, TransitStopFacility.class));
        TransitStopFacility fromStopH = f.schedule.getFacilities().get(Id.create(15, TransitStopFacility.class));
        double depTime = 7*3600 + 30*60;
        List<TransitStopFacility> fromStops = new ArrayList<>();
        fromStops.add(fromStopB);
        fromStops.add(fromStopH);
        Map<Id<TransitStopFacility>, TravelInfo> map = raptor.calcTree(fromStops, depTime, raptorParams);

        Assert.assertEquals("wrong number of reached stops.", 22, map.size());

        Assert.assertNull(map.get(Id.create(0, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 1 , 0, "08:34:00"); // from H, directly reachable
        assertTravelInfo(map, 2 , 0, "07:30:00"); // from B, we started here
        assertTravelInfo(map, 3 , 0, "08:29:00"); // from H, directly reachable
        assertTravelInfo(map, 4 , 0, "07:38:00"); // from B, directly reachable
        assertTravelInfo(map, 5 , 0, "08:18:00"); // from H, directly reachable
        assertTravelInfo(map, 6 , 0, "07:49:00"); // from B, directly reachable
        assertTravelInfo(map, 7 , 0, "08:13:00"); // from H, directly reachable
        assertTravelInfo(map, 8 , 0, "07:56:00"); // from B, directly reachable
        assertTravelInfo(map, 9 , 0, "08:06:00"); // from H, directly reachable
        assertTravelInfo(map, 10, 0, "08:03:00"); // from B, directly reachable
        assertTravelInfo(map, 11, 0, "07:59:00"); // from H, directly reachable
        assertTravelInfo(map, 12, 0, "08:08:00"); // from B, directly reachable
        assertTravelInfo(map, 13, 0, "07:48:00"); // from H, directly reachable
        assertTravelInfo(map, 14, 0, "08:19:00"); // from B, directly reachable
        assertTravelInfo(map, 15, 0, "07:30:00"); // from H, we started here
        assertTravelInfo(map, 16, 0, "08:24:00"); // from B, directly reachable
        Assert.assertNull(map.get(Id.create(17, TransitStopFacility.class))); // unreachable
        assertTravelInfo(map, 18, 0, "07:38:03"); // from B, transfer at C, 7:38 (walk 2m)
        assertTravelInfo(map, 19, 1, "07:51:00"); // from B, transfer at C, 7:38/7:41 green
        assertTravelInfo(map, 20, 1, "08:01:00"); // from B, transfer at C, 7:38/7:41 green
        assertTravelInfo(map, 21, 0, "07:48:03"); // from H, transfer at G (walk)
        assertTravelInfo(map, 22, 1, "08:01:00"); // from H, transfer at G, 7:48/7:51 green
        assertTravelInfo(map, 23, 1, "08:11:00"); // from H, transfer at G, 7:48/7:51 green
    }

    private void assertTravelInfo(Map<Id<TransitStopFacility>, TravelInfo> map, int stopId, int expectedTransfers, String expectedArrivalTime) {
        TravelInfo info = map.get(Id.create(stopId, TransitStopFacility.class));
        Assert.assertNotNull("Stop " + stopId + " is not reachable.", info);
        Assert.assertEquals("wrong number of transfers", expectedTransfers, info.transferCount);
        Assert.assertEquals("unexpected arrival time: " + Time.writeTime(info.arrivalTime), Time.parseTime(expectedArrivalTime), Math.floor(info.arrivalTime), 0.0);
    }
}
