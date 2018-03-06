/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.core.controler.AbstractModule;
import org.matsim.pt.router.TransitRouter;

/**
 * @author mrieser / SBB
 */
public class SwissRailRaptorModule extends AbstractModule {

    @Override
    public void install() {
        if (getConfig().transit().isUseTransit()) {
            bind(TransitRouter.class).toProvider(SwissRailRaptorFactory.class);
            bind(RaptorRouteSelector.class).to(LeastCostRaptorRouteSelector.class);
        }
    }

}
