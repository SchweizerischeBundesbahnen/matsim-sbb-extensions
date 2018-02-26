/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import com.google.inject.Module;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.qsim.AbstractQSimPlugin;
import org.matsim.core.replanning.ReplanningContext;

import java.util.Collection;
import java.util.Collections;

/**
 * The deterministic transit simulation ({@link SBBTransitQSimEngine}) requires access
 * to the current iteration number to determine if link-events should be created or not.
 * This iteration number is accessible using {@link ReplanningContext}. In order to run
 * the tests, we have a special test module that registers an instance of a ReplanningContext
 * in order to run the tests successfully. This module does exactly that: it registers a
 * special ReplanningContext for the tests.
 *
 * @author mrieser / SBB
 */
public class TestQSimModule extends AbstractQSimPlugin {

    public final DummyReplanningContext context;

    public TestQSimModule(Config config) {
        super(config);
        this.context = new DummyReplanningContext();
    }

    @Override
    public Collection<? extends Module> modules() {
        return Collections.singletonList(new com.google.inject.AbstractModule() {
            @Override
            protected void configure() {
                bind(ReplanningContext.class).toInstance(context);
            }
        });
    }

    public static final class DummyReplanningContext implements ReplanningContext {

        private int iteration = 0;

        public void setIteration(int iteration) {
            this.iteration = iteration;
        }

        @Override
        public int getIteration() {
            return this.iteration;
        }
    }
}
