/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.PtConstants;
import org.matsim.testcases.MatsimTestUtils;

import java.util.List;

/**
 * @author mrieser / SBB
 */
public class SwissRailRaptorModuleTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Before
    public void setUp() {
        System.setProperty("matsim.preferLocalDtds", "true");
    }

    @Test
    public void testInitialization() {
        Config config = ConfigUtils.createConfig();
        config.controler().setLastIteration(0);
        config.controler().setOutputDirectory(this.utils.getOutputDirectory());
        config.controler().setCreateGraphs(false);
        config.controler().setDumpDataAtEnd(false);
        config.transit().setUseTransit(true);
        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SwissRailRaptorModule());
            }
        });
        controler.run();

        TripRouter tripRouter = controler.getInjector().getInstance(TripRouter.class);

        // this test mostly checks that no exception occurred

        RoutingModule module = tripRouter.getRoutingModule(TransportMode.pt);
        Assert.assertTrue(module instanceof SwissRailRaptorRoutingModule);
    }

    @Test
    public void testIntermodalIntegration() {
        IntermodalFixture f = new IntermodalFixture();

        // add a single agent traveling with (intermodal) pt from A to B

        Population pop = f.scenario.getPopulation();
        PopulationFactory pf = pop.getFactory();
        Person p1 = pf.createPerson(Id.create(1, Person.class));
        pop.addPerson(p1);
        Plan plan = pf.createPlan();
        p1.addPlan(plan);
        Activity homeAct = pf.createActivityFromCoord("home", new Coord(10000, 10500));
        homeAct.setEndTime(7*3600);
        plan.addActivity(homeAct);
        plan.addLeg(pf.createLeg(TransportMode.pt));
        plan.addActivity(pf.createActivityFromCoord("work", new Coord(50000, 10500)));

        // prepare intermodal swissrailraptor

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

        // prepare scoring
        Config config = f.config;
        PlanCalcScoreConfigGroup.ActivityParams homeScoring = new PlanCalcScoreConfigGroup.ActivityParams("home");
        homeScoring.setTypicalDuration(16*3600);
        f.config.planCalcScore().addActivityParams(homeScoring);
        PlanCalcScoreConfigGroup.ActivityParams workScoring = new PlanCalcScoreConfigGroup.ActivityParams("work");
        workScoring.setTypicalDuration(8*3600);
        f.config.planCalcScore().addActivityParams(workScoring);

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        // prepare rest of config

        config.controler().setLastIteration(0);
        config.controler().setOutputDirectory(this.utils.getOutputDirectory());
        config.controler().setCreateGraphs(false);
        config.controler().setDumpDataAtEnd(false);
        config.qsim().setEndTime(10*3600);
        config.transit().setUseTransit(true);
        Controler controler = new Controler(f.scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SwissRailRaptorModule());
            }
        });

        // run a single iteration
        controler.run();

        // test that swiss rail raptor was used
        TripRouter tripRouter = controler.getInjector().getInstance(TripRouter.class);
        RoutingModule module = tripRouter.getRoutingModule(TransportMode.pt);
        Assert.assertTrue(module instanceof SwissRailRaptorRoutingModule);

        // also test that our one agent got correctly routed with intermodal access
        List<PlanElement> planElements = plan.getPlanElements();
        for (PlanElement pe : planElements) {
            System.out.println(pe);
        }

        Assert.assertEquals("wrong number of PlanElements.", 11, planElements.size());
        Assert.assertTrue(planElements.get(0) instanceof Activity);
        Assert.assertTrue(planElements.get(1) instanceof Leg);
        Assert.assertTrue(planElements.get(2) instanceof Activity);
        Assert.assertTrue(planElements.get(3) instanceof Leg);
        Assert.assertTrue(planElements.get(4) instanceof Activity);
        Assert.assertTrue(planElements.get(5) instanceof Leg);
        Assert.assertTrue(planElements.get(6) instanceof Activity);
        Assert.assertTrue(planElements.get(7) instanceof Leg);
        Assert.assertTrue(planElements.get(8) instanceof Activity);
        Assert.assertTrue(planElements.get(9) instanceof Leg);
        Assert.assertTrue(planElements.get(10) instanceof Activity);

        Assert.assertEquals("home", ((Activity) planElements.get(0)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(2)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(4)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(6)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(8)).getType());
        Assert.assertEquals("work", ((Activity) planElements.get(10)).getType());

        Assert.assertEquals(TransportMode.bike, ((Leg) planElements.get(1)).getMode());
        Assert.assertEquals(TransportMode.transit_walk, ((Leg) planElements.get(3)).getMode());
        Assert.assertEquals(TransportMode.pt, ((Leg) planElements.get(5)).getMode());
        Assert.assertEquals(TransportMode.transit_walk, ((Leg) planElements.get(7)).getMode());
        Assert.assertEquals(TransportMode.bike, ((Leg) planElements.get(9)).getMode());

        Assert.assertEquals(0.0, ((Activity) planElements.get(2)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(4)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(6)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(8)).getMaximumDuration(), 0.0);
    }

}