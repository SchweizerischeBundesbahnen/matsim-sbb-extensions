/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mobsim.qsim;

import ch.sbb.matsim.config.SBBTransitConfigGroup;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author mrieser / SBB
 */
public class SBBQSimModuleTest {

    @Rule public MatsimTestUtils utils = new MatsimTestUtils();

    @Before
    public void setUp() {
        System.setProperty("matsim.preferLocalDtds", "true");
    }

    // https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions/issues/3
    @Test
    public void testIntegration() {
        String xmlConfig = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">\n" +
                "<config>\n" +
                "\t<module name=\"controler\" >\n" +
                "\t\t<param name=\"createGraphs\" value=\"false\" />\n" +
                "\t\t<param name=\"dumpDataAtEnd\" value=\"false\" />\n" +
                "\t\t<param name=\"lastIteration\" value=\"0\" />\n" +
                "\t</module>\n" +
                "\t<module name=\"SBBPt\" >\n" +
                "\t\t<param name=\"deterministicServiceModes\" value=\"train,metro\" />\n" +
                "\t\t<param name=\"createLinkEventsInterval\" value=\"10\" />\n" +
                "\t</module>\n" +
                "</config>";

        Config config = ConfigUtils.createConfig();
        new ConfigReader(config).parse(new ByteArrayInputStream(xmlConfig.getBytes(StandardCharsets.UTF_8)));
        config.controler().setOutputDirectory(this.utils.getOutputDirectory());
        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
            install(new SBBQSimModule());
            }
        });
        controler.run();

        // this test mostly checks that no exception occurred

        Assert.assertTrue(config.getModules().get(SBBTransitConfigGroup.GROUP_NAME) instanceof SBBTransitConfigGroup);
    }

}