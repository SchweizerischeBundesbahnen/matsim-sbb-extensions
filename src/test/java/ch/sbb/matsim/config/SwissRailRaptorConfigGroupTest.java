/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.config;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.ModeMappingForPassengersParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.RangeQuerySettingsParameterSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author mrieser / SBB
 */
public class SwissRailRaptorConfigGroupTest {

    @Before
    public void setup() {
        System.setProperty("matsim.preferLocalDtds", "true");
    }

    @Test
    public void testConfigIO_general() {
        SwissRailRaptorConfigGroup config1 = new SwissRailRaptorConfigGroup();

        { // prepare config1
            config1.setUseRangeQuery(true);
            config1.setUseIntermodalAccessEgress(true);
            config1.setUseModeMappingForPassengers(true);
        }

        SwissRailRaptorConfigGroup config2 = writeRead(config1);

        // do checks
        Assert.assertTrue(config2.isUseRangeQuery());
        Assert.assertTrue(config2.isUseIntermodalAccessEgress());
        Assert.assertTrue(config2.isUseModeMappingForPassengers());
    }

    @Test
    public void testConfigIO_rangeQuery() {
        SwissRailRaptorConfigGroup config1 = new SwissRailRaptorConfigGroup();

        { // prepare config1
            config1.setUseRangeQuery(true);

            RangeQuerySettingsParameterSet range1 = new RangeQuerySettingsParameterSet();
            range1.setSubpopulation(null);
            range1.setMaxEarlierDeparture(10*60);
            range1.setMaxLaterDeparture(59*60);
            config1.addRangeQuerySettings(range1);

            RangeQuerySettingsParameterSet range2 = new RangeQuerySettingsParameterSet();
            range2.setSubpopulation("inflexible");
            range2.setMaxEarlierDeparture(1*60);
            range2.setMaxLaterDeparture(15*60);
            config1.addRangeQuerySettings(range2);
        }

        SwissRailRaptorConfigGroup config2 = writeRead(config1);

        // do checks
        Assert.assertTrue(config2.isUseRangeQuery());

        RangeQuerySettingsParameterSet range1 = config2.getRangeQuerySettings(null);
        Assert.assertNotNull(range1);
        Assert.assertNull(range1.getSubpopulation());
        Assert.assertEquals(10*60, range1.getMaxEarlierDeparture());
        Assert.assertEquals(59*60, range1.getMaxLaterDeparture());

        RangeQuerySettingsParameterSet range2 = config2.getRangeQuerySettings("inflexible");
        Assert.assertNotNull(range2);
        Assert.assertEquals("inflexible", range2.getSubpopulation());
        Assert.assertEquals(1*60, range2.getMaxEarlierDeparture());
        Assert.assertEquals(15*60, range2.getMaxLaterDeparture());
    }

    @Test
    public void testConfigIO_intermodalAccessEgress() {
        SwissRailRaptorConfigGroup config1 = new SwissRailRaptorConfigGroup();

        { // prepare config1
            config1.setUseIntermodalAccessEgress(true);

            IntermodalAccessEgressParameterSet paramset1 = new IntermodalAccessEgressParameterSet();
            paramset1.setMode(TransportMode.bike);
            paramset1.setRadius(2000);
            paramset1.setSubpopulation(null);
            paramset1.setFilterAttribute("bikeAndRail");
            paramset1.setFilterValue("true");
            config1.addIntermodalAccessEgress(paramset1);

            IntermodalAccessEgressParameterSet paramset2 = new IntermodalAccessEgressParameterSet();
            paramset2.setMode("sff");
            paramset2.setRadius(5000);
            paramset2.setSubpopulation("sff_users");
            paramset2.setLinkIdAttribute("linkId_sff");
            paramset2.setFilterAttribute("stop-type");
            paramset2.setFilterValue("hub");
            config1.addIntermodalAccessEgress(paramset2);
        }

        SwissRailRaptorConfigGroup config2 = writeRead(config1);

        // do checks
        Assert.assertTrue(config2.isUseIntermodalAccessEgress());

        List<IntermodalAccessEgressParameterSet> parameterSets = config2.getIntermodalAccessEgressParameterSets();
        Assert.assertNotNull(parameterSets);
        Assert.assertEquals("wrong number of parameter sets",2, parameterSets.size());

        IntermodalAccessEgressParameterSet paramSet1 = parameterSets.get(0);
        Assert.assertEquals(TransportMode.bike, paramSet1.getMode());
        Assert.assertEquals(2000, paramSet1.getRadius(), 0.0);
        Assert.assertNull(paramSet1.getSubpopulation());
        Assert.assertNull(paramSet1.getLinkIdAttribute());
        Assert.assertEquals("bikeAndRail", paramSet1.getFilterAttribute());
        Assert.assertEquals("true", paramSet1.getFilterValue());

        IntermodalAccessEgressParameterSet paramSet2 = parameterSets.get(1);
        Assert.assertEquals("sff", paramSet2.getMode());
        Assert.assertEquals(5000, paramSet2.getRadius(), 0.0);
        Assert.assertEquals("sff_users", paramSet2.getSubpopulation());
        Assert.assertEquals("linkId_sff", paramSet2.getLinkIdAttribute());
        Assert.assertEquals("stop-type", paramSet2.getFilterAttribute());
        Assert.assertEquals("hub", paramSet2.getFilterValue());
    }

    @Test
    public void testConfigIO_modeMappings() {
        SwissRailRaptorConfigGroup config1 = new SwissRailRaptorConfigGroup();

        { // prepare config1
            config1.setUseModeMappingForPassengers(true);

            ModeMappingForPassengersParameterSet mapping1 = new ModeMappingForPassengersParameterSet();
            mapping1.setRouteMode("train");
            mapping1.setPassengerMode("rail");
            config1.addModeMappingForPassengers(mapping1);

            ModeMappingForPassengersParameterSet mapping2 = new ModeMappingForPassengersParameterSet();
            mapping2.setRouteMode("tram");
            mapping2.setPassengerMode("rail");
            config1.addModeMappingForPassengers(mapping2);

            ModeMappingForPassengersParameterSet mapping3 = new ModeMappingForPassengersParameterSet();
            mapping3.setRouteMode("bus");
            mapping3.setPassengerMode("road");
            config1.addModeMappingForPassengers(mapping3);
        }

        SwissRailRaptorConfigGroup config2 = writeRead(config1);

        // do checks
        Assert.assertTrue(config2.isUseModeMappingForPassengers());

        ModeMappingForPassengersParameterSet trainMapping = config2.getModeMappingForPassengersParameterSet("train");
        Assert.assertNotNull(trainMapping);
        Assert.assertEquals("train", trainMapping.getRouteMode());
        Assert.assertEquals("rail", trainMapping.getPassengerMode());

        ModeMappingForPassengersParameterSet tramMapping = config2.getModeMappingForPassengersParameterSet("tram");
        Assert.assertNotNull(tramMapping);
        Assert.assertEquals("tram", tramMapping.getRouteMode());
        Assert.assertEquals("rail", tramMapping.getPassengerMode());

        ModeMappingForPassengersParameterSet busMapping = config2.getModeMappingForPassengersParameterSet("bus");
        Assert.assertNotNull(busMapping);
        Assert.assertEquals("bus", busMapping.getRouteMode());
        Assert.assertEquals("road", busMapping.getPassengerMode());

        Assert.assertNull(config2.getModeMappingForPassengersParameterSet("road"));
        Assert.assertNull(config2.getModeMappingForPassengersParameterSet("ship"));
    }

    private SwissRailRaptorConfigGroup writeRead(SwissRailRaptorConfigGroup config) {
        Config fullConfig1 = ConfigUtils.createConfig(config);

        // write config1
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(output);
        new ConfigWriter(fullConfig1).writeStream(writer);
        new ConfigWriter(fullConfig1).writeStream(new PrintWriter(System.out));

        // read config in again as config2
        SwissRailRaptorConfigGroup config2 = new SwissRailRaptorConfigGroup();
        Config fullConfig2 = ConfigUtils.createConfig(config2);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        new ConfigReader(fullConfig2).parse(input);

        return config2;
    }
}
