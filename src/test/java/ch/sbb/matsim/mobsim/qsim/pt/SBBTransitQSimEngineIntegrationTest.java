/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.pt.TransitQSimEngine;
import org.matsim.testcases.MatsimTestUtils;

import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;

import java.util.HashSet;
import java.util.Set;

/**
 * @author mrieser / SBB
 */
public class SBBTransitQSimEngineIntegrationTest {

    @Rule public MatsimTestUtils utils = new MatsimTestUtils();

    private static final Logger log = Logger.getLogger(SBBTransitQSimEngineIntegrationTest.class);

    @Test
    @Ignore // Unfortunately, this check does not work anymore. Something like QSim.getMobsimEngines() or similar would be useful. /sh sep'18
    public void testIntegration() {
        TestFixture f = new TestFixture();

        f.config.controler().setOutputDirectory(this.utils.getOutputDirectory());
        f.config.controler().setLastIteration(0);

        Controler controler = new Controler(f.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SBBTransitModule());
            }
        });

        controler.run();

        Mobsim mobsim = controler.getInjector().getInstance(Mobsim.class);
        Assert.assertNotNull(mobsim);
        Assert.assertEquals(QSim.class, mobsim.getClass());

        QSim qsim = (QSim) mobsim;
        TransitQSimEngine trEngine = qsim.getChildInjector().getInstance(SBBTransitQSimEngine.class);
        Assert.assertNotNull(trEngine);
        try {
            trEngine = qsim.getChildInjector().getInstance(TransitQSimEngine.class);
            Assert.fail("expected exception, got none. " + trEngine);
        } catch (RuntimeException expected) {
            // ignore
        }
    }

    @Test
    @Ignore // Unfortunately, this check does not work anymore. Something like QSim.getMobsimEngines() or similar would be useful. /sh sep'18
    public void testIntegration_misconfiguration() {
        TestFixture f = new TestFixture();

        Set<String> mainModes = new HashSet<>();
        mainModes.add("car");
        mainModes.add("train");
        f.config.qsim().setMainModes(mainModes);
        f.config.controler().setOutputDirectory(this.utils.getOutputDirectory());
        f.config.controler().setLastIteration(0);

        Controler controler = new Controler(f.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SBBTransitModule());
            }
        });

        try {
            controler.run();
            Assert.fail("Expected exception, got none.");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().endsWith("This will not work! common modes = train"));
        }
    }

}
