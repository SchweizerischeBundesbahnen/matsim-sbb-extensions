/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.pt.TransitQSimEngine;
import org.matsim.testcases.MatsimTestUtils;

import ch.sbb.matsim.mobsim.qsim.SBBQSimModule;

/**
 * @author mrieser / SBB
 */
public class SBBTransitQSimEngineIntegrationTest {

    @Rule public MatsimTestUtils utils = new MatsimTestUtils();

    private static final Logger log = Logger.getLogger(SBBTransitQSimEngineIntegrationTest.class);

    @Test
    public void testIntegration() {
        TestFixture f = new TestFixture();

        f.config.controler().setOutputDirectory(this.utils.getOutputDirectory());
        f.config.controler().setLastIteration(0);

        Controler controler = new Controler(f.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new SBBQSimModule());
            }
        });

        controler.run();

        Mobsim mobsim = controler.getInjector().getInstance(Mobsim.class);
        Assert.assertNotNull(mobsim);
        Assert.assertEquals(QSim.class, mobsim.getClass());

        QSim qSim = (QSim) mobsim;
        TransitQSimEngine trEngine = qSim.getTransitEngine();
        Assert.assertEquals(SBBTransitQSimEngine.class, trEngine.getClass());
    }

}
