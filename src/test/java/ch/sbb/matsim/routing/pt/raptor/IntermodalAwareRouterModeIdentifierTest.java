/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.PtConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author mrieser / SBB
 */
public class IntermodalAwareRouterModeIdentifierTest {

    @Test
    public void testSimplePt() {
        Config config = ConfigUtils.createConfig();
        IntermodalAwareRouterModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(config);
        List<PlanElement> tripElements = Collections.singletonList(
                PopulationUtils.createLeg(TransportMode.pt)
        );
        String identifiedMode = identifier.identifyMainMode(tripElements);
        Assert.assertEquals(TransportMode.pt, identifiedMode);
    }

    @Test
    public void testPtWithAccessEgress() {
        Config config = ConfigUtils.createConfig();
        IntermodalAwareRouterModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(config);
        List<PlanElement> tripElements = Arrays.asList(
                PopulationUtils.createLeg(TransportMode.access_walk),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.pt),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.egress_walk)
        );
        String identifiedMode = identifier.identifyMainMode(tripElements);
        Assert.assertEquals(TransportMode.pt, identifiedMode);
    }

    @Test
    public void testPtWithIntermodalAccessEgress() {
        Config config = ConfigUtils.createConfig();
        IntermodalAwareRouterModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(config);
        List<PlanElement> tripElements = Arrays.asList(
                PopulationUtils.createLeg(TransportMode.access_walk),
                PopulationUtils.createActivityFromCoord("bike interaction", new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.pt),
                PopulationUtils.createLeg(TransportMode.egress_walk),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.transit_walk),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.pt),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.egress_walk)
        );
        String identifiedMode = identifier.identifyMainMode(tripElements);
        Assert.assertEquals(TransportMode.pt, identifiedMode);
    }

    @Test
    public void testPtCustomModes() {
        Config config = ConfigUtils.createConfig();
        config.transit().setTransitModes(CollectionUtils.stringToSet("train,bus"));
        IntermodalAwareRouterModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(config);
        List<PlanElement> tripElements = Arrays.asList(
                PopulationUtils.createLeg(TransportMode.access_walk),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg("train"),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.egress_walk)
        );
        String identifiedMode = identifier.identifyMainMode(tripElements);
        Assert.assertEquals("train", identifiedMode);
    }

    @Test
    public void testPtWalkOnly() {
        Config config = ConfigUtils.createConfig();
        IntermodalAwareRouterModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(config);
        List<PlanElement> tripElements = Collections.singletonList(
                PopulationUtils.createLeg(TransportMode.transit_walk)
        );
        String identifiedMode = identifier.identifyMainMode(tripElements);
        Assert.assertEquals(TransportMode.pt, identifiedMode);
    }

    @Test
    public void testNonPt() {
        Config config = ConfigUtils.createConfig();
        IntermodalAwareRouterModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(config);
        List<PlanElement> tripElements = Collections.singletonList(
                PopulationUtils.createLeg(TransportMode.bike)
        );
        String identifiedMode = identifier.identifyMainMode(tripElements);
        Assert.assertEquals(TransportMode.bike, identifiedMode);
    }

    @Test
    public void testNonPtWithAccessEgress() {
        Config config = ConfigUtils.createConfig();
        IntermodalAwareRouterModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(config);
        List<PlanElement> tripElements = Arrays.asList(
                PopulationUtils.createLeg(TransportMode.access_walk),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.bike),
                PopulationUtils.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(0, 0)),
                PopulationUtils.createLeg(TransportMode.egress_walk)
        );
        String identifiedMode = identifier.identifyMainMode(tripElements);
        Assert.assertEquals(TransportMode.bike, identifiedMode);
    }

}
