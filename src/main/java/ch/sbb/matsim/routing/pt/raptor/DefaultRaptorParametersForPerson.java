/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;

import javax.inject.Inject;

/**
 * A default implementation of {@link RaptorParametersForPerson} returning the
 * same parameters for every person.
 *
 * @author mrieser / SBB
 */
public class DefaultRaptorParametersForPerson implements RaptorParametersForPerson {

    private final RaptorConfig defaultParameters;

    @Inject
    public DefaultRaptorParametersForPerson(Config config) {
        this.defaultParameters = RaptorUtils.createRaptorConfig(config);
    }

    @Override
    public RaptorConfig getRaptorParameters(Person person) {
        return this.defaultParameters;
    }
}
