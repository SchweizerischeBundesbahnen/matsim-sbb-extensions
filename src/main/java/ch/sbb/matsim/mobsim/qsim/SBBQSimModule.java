/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mobsim.qsim;

import java.util.ArrayList;
import java.util.Collection;

import ch.sbb.matsim.config.SBBTransitConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.mobsim.qsim.AbstractQSimPlugin;
import org.matsim.core.mobsim.qsim.ActivityEnginePlugin;
import org.matsim.core.mobsim.qsim.PopulationPlugin;
import org.matsim.core.mobsim.qsim.TeleportationPlugin;
import org.matsim.core.mobsim.qsim.changeeventsengine.NetworkChangeEventsPlugin;
import org.matsim.core.mobsim.qsim.messagequeueengine.MessageQueuePlugin;
import org.matsim.core.mobsim.qsim.pt.ComplexTransitStopHandlerFactory;
import org.matsim.core.mobsim.qsim.pt.TransitStopHandlerFactory;
import org.matsim.core.mobsim.qsim.qnetsimengine.QNetsimEnginePlugin;

import com.google.inject.Provides;

import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEnginePlugin;

/**
 * @author mrieser / SBB
 */
public class SBBQSimModule extends AbstractModule {

    @Override
    public void install() {
        bind(TransitStopHandlerFactory.class).to(ComplexTransitStopHandlerFactory.class).asEagerSingleton();

        // make sure the config is registered before the simulation starts
        // https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions/issues/3
        ConfigUtils.addOrGetModule(getConfig(), SBBTransitConfigGroup.class);
    }

    // @SuppressWarnings("static-method")
    @Provides
    Collection<AbstractQSimPlugin> provideQSimPlugins(Config config) {
        final Collection<AbstractQSimPlugin> plugins = new ArrayList<>();
        plugins.add(new MessageQueuePlugin(config));
        plugins.add(new ActivityEnginePlugin(config));
        plugins.add(new QNetsimEnginePlugin(config));
        if (config.network().isTimeVariantNetwork()) {
            plugins.add(new NetworkChangeEventsPlugin(config));
        }
        if (config.transit().isUseTransit()) {
            plugins.add(new SBBTransitEnginePlugin(config));
        }
        plugins.add(new TeleportationPlugin(config));
        plugins.add(new PopulationPlugin(config));
        return plugins;
    }

}
