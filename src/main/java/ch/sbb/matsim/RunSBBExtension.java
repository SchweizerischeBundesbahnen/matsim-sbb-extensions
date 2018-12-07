/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim;

import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfig;
import org.matsim.core.mobsim.qsim.components.StandardQSimComponentConfigurator;
import org.matsim.core.scenario.ScenarioUtils;

import com.google.inject.Provides;

/**
 * Example script that shows how to use the extensions
 * developed by SBB (Swiss Federal Railway) included in this repository.
 *
 * @author mrieser / SBB
 */
public class RunSBBExtension {

	private static Logger log = Logger.getLogger(RunSBBExtension.class);

	public static void main(String[] args) {
		String configFilename = args[0];
		Config config = ConfigUtils.loadConfig(configFilename);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				// To use the deterministic pt simulation (Part 1 of 2):
				install(new SBBTransitModule());

				// To use the fast pt router (Part 1 of 1)
				install(new SwissRailRaptorModule());
			}

			// To use the deterministic pt simulation (Part 2 of 2):
			@Provides
			QSimComponentsConfig provideQSimComponentsConfig() {
				QSimComponentsConfig components = new QSimComponentsConfig();
				new StandardQSimComponentConfigurator(config).configure(components);
				SBBTransitEngineQSimModule.configure(components);
				return components;
			}
		});

		controler.run();
	}

}
