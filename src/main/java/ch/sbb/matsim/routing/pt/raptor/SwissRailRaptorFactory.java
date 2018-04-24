/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.router.RoutingModule;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
@Singleton
public class SwissRailRaptorFactory implements Provider<SwissRailRaptor> {

    private SwissRailRaptorData data = null;
    private final TransitSchedule schedule;
    private final RaptorStaticConfig raptorConfig;
    private final RaptorParametersForPerson raptorParametersForPerson;
    private final RaptorRouteSelector routeSelector;
    private final RaptorIntermodalAccessEgress intermodalAE;

    private final Network network;
    private final PlansConfigGroup plansConfigGroup;
    private final Population population;
    private final Map<String, Provider<RoutingModule>> routingModuleProviders;

    @Inject
    public SwissRailRaptorFactory(final TransitSchedule schedule, final Config config, final Network network,
                                  RaptorParametersForPerson raptorParametersForPerson, RaptorRouteSelector routeSelector,
                                  RaptorIntermodalAccessEgress intermodalAE, PlansConfigGroup plansConfigGroup, Population population,
                                  Map<String, Provider<RoutingModule>> routingModules) {
        this.schedule = schedule;
        this.raptorConfig = RaptorUtils.createStaticConfig(config);
        this.network = network;
        this.raptorParametersForPerson = raptorParametersForPerson;
        this.routeSelector = routeSelector;
        this.intermodalAE = intermodalAE;
        this.plansConfigGroup = plansConfigGroup;
        this.population = population;

        SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        this.routingModuleProviders = new HashMap<>();
        if (srrConfig.isUseIntermodalAccessEgress()) {
            for (IntermodalAccessEgressParameterSet params : srrConfig.getIntermodalAccessEgressParameterSets()) {
                String mode = params.getMode();
                this.routingModuleProviders.put(mode, routingModules.get(mode));
            }
        }
    }

    @Override
    public SwissRailRaptor get() {
        SwissRailRaptorData data = getData();
        Map<String, RoutingModule> neededRoutingModules = new HashMap<>();
        for (Map.Entry<String, Provider<RoutingModule>> e : this.routingModuleProviders.entrySet()) {
            String mode = e.getKey();
            RoutingModule module = e.getValue().get();
            neededRoutingModules.put(mode, module);
        }
        return new SwissRailRaptor(data, this.raptorParametersForPerson, this.routeSelector, this.intermodalAE,
                this.plansConfigGroup.getSubpopulationAttributeName(), this.population.getPersonAttributes(), neededRoutingModules);
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
