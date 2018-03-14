/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.router.TripRouter;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author mrieser / SBB
 */
@Singleton
public class SwissRailRaptorFactory implements Provider<SwissRailRaptor> {

    private SwissRailRaptorData data = null;
    private final TransitSchedule schedule;
    private final RaptorConfig raptorConfig;
    private final RaptorParametersForPerson raptorParametersForPerson;
    private final RaptorRouteSelector routeSelector;

    private Network network;
    private PlansConfigGroup plansConfigGroup;
    private Population population;
    private Provider<TripRouter> tripRouterProvider;

    @Inject
    public SwissRailRaptorFactory(final TransitSchedule schedule, final Config config, final Network network,
                                  RaptorParametersForPerson raptorParametersForPerson, RaptorRouteSelector routeSelector,
                                  PlansConfigGroup plansConfigGroup, Population population, Provider<TripRouter> tripRouterProvider) {
        this.schedule = schedule;
        this.raptorConfig = RaptorUtils.createRaptorConfig(config);
        this.network = network;
        this.raptorParametersForPerson = raptorParametersForPerson;
        this.routeSelector = routeSelector;
        this.plansConfigGroup = plansConfigGroup;
        this.population = population;
        this.tripRouterProvider = tripRouterProvider;
    }

    @Override
    public SwissRailRaptor get() {
        SwissRailRaptorData data = getData();
        TripRouter tripRouter = this.tripRouterProvider.get();
        return new SwissRailRaptor(data, this.raptorParametersForPerson, this.routeSelector,
                this.plansConfigGroup.getSubpopulationAttributeName(), this.population.getPersonAttributes(), tripRouter);
    }

    private SwissRailRaptorData getData() {
        if (this.data == null) {
            this.data = prepareData();
        }
        return this.data;
    }

    synchronized private SwissRailRaptorData prepareData() {
        if (this.data != null) {
            // due to multithreading / race conditions, this could still happen.
            // prevent doing the work twice.
            return this.data;
        }
        this.data = SwissRailRaptorData.create(this.schedule, this.raptorConfig, this.network);
        return this.data;
    }

}
