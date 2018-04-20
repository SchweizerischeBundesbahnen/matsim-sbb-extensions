/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import java.util.Collection;
import java.util.Collections;

import org.matsim.core.config.Config;
import org.matsim.core.mobsim.framework.AgentSource;
import org.matsim.core.mobsim.qsim.AbstractQSimPlugin;
import org.matsim.core.mobsim.qsim.interfaces.DepartureHandler;
import org.matsim.core.mobsim.qsim.interfaces.MobsimEngine;

import com.google.inject.Module;

public class SBBTransitEnginePlugin extends AbstractQSimPlugin {

    public SBBTransitEnginePlugin(Config config) {
        super(config);
    }

    @Override
    public Collection<? extends Module> modules() {
        return Collections.singletonList(new com.google.inject.AbstractModule() {
            @Override
            protected void configure() {
                bind(SBBTransitQSimEngine.class).asEagerSingleton();
            }
        });
    }

    @Override
    public Collection<Class<? extends DepartureHandler>> departureHandlers() {
        return Collections.singletonList(SBBTransitQSimEngine.class);
    }

    @Override
    public Collection<Class<? extends AgentSource>> agentSources() {
        return Collections.singletonList(SBBTransitQSimEngine.class);
    }

    @Override
    public Collection<Class<? extends MobsimEngine>> engines() {
        return Collections.singletonList(SBBTransitQSimEngine.class);
    }
}
