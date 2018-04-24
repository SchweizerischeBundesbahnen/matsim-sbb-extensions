/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.population.PlanElement;

import java.util.List;

/**
 * @author pmanser / SBB
 */
public interface RaptorIntermodalAccessEgress {

    RIntermodalAccessEgress calcIntermodalAccessEgress(List<? extends PlanElement> legs, RaptorParameters params);

    class RIntermodalAccessEgress {

        final List<? extends PlanElement> routeParts;
        final double disutility;
        final double travelTime;

        public RIntermodalAccessEgress(List<? extends PlanElement> planElements, double disutility, double travelTime) {
            this.routeParts = planElements;
            this.disutility = disutility;
            this.travelTime = travelTime;
        }
    }
}
