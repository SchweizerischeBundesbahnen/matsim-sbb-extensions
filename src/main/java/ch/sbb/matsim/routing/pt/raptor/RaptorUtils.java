/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.routes.ExperimentalTransitRoute;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mrieser / SBB
 */
public final class RaptorUtils {

    private RaptorUtils() {
    }

    public static RaptorParameters createRaptorParameters(Config config) {
        SwissRailRaptorConfigGroup advancedConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);

        TransitRouterConfig trConfig = new TransitRouterConfig(config);
        RaptorParameters raptorParams = new RaptorParameters(advancedConfig, config.planCalcScore());
        raptorParams.setBeelineWalkConnectionDistance(trConfig.getBeelineWalkConnectionDistance());
        raptorParams.setBeelineWalkSpeed(trConfig.getBeelineWalkSpeed());

        raptorParams.setSearchRadius(trConfig.getSearchRadius());
        raptorParams.setExtensionRadius(trConfig.getExtensionRadius());

        raptorParams.setMinimalTransferTime(trConfig.getAdditionalTransferTime());

        raptorParams.setMarginalUtilityOfTravelTimeWalk_utl_s(trConfig.getMarginalUtilityOfTravelTimeWalk_utl_s());
        raptorParams.setMarginalUtilityOfTravelTimeAccessWalk_utl_s(trConfig.getMarginalUtilityOfTravelTimeWalk_utl_s());
        raptorParams.setMarginalUtilityOfTravelTimeEgressWalk_utl_s(trConfig.getMarginalUtilityOfTravelTimeWalk_utl_s());
        raptorParams.setMarginalUtilityOfTravelTimePt_utl_s(trConfig.getMarginalUtilityOfTravelTimePt_utl_s());
        raptorParams.setMarginalUtilityOfWaitingPt_utl_s(trConfig.getMarginalUtilityOfWaitingPt_utl_s());

        raptorParams.setTransferPenaltyCost(-trConfig.getUtilityOfLineSwitch_utl());

        raptorParams.setSubpopulationAttribute(config.plans().getSubpopulationAttributeName());

        return raptorParams;
    }

    public static List<Leg> convertRouteToLegs(RaptorRoute route) {
        List<Leg> legs = new ArrayList<>(route.parts.size());
        for (RaptorRoute.RoutePart part : route.parts) {
            if (part.planElements != null) {
                for (PlanElement pe : part.planElements) {
                    if (pe instanceof Leg) {
                        legs.add((Leg) pe);
                    }
                }
            } else if (part.line != null) {
                // a pt leg
                Leg ptLeg = PopulationUtils.createLeg(part.mode);
                ptLeg.setDepartureTime(part.depTime);
                ptLeg.setTravelTime(part.travelTime);
                ExperimentalTransitRoute ptRoute = new ExperimentalTransitRoute(part.fromStop, part.line, part.route, part.toStop);
                ptRoute.setTravelTime(part.travelTime);
                ptRoute.setDistance(part.distance);
                ptLeg.setRoute(ptRoute);
                legs.add(ptLeg);
            } else {
                // a non-pt leg
                Leg walkLeg = PopulationUtils.createLeg(part.mode);
                walkLeg.setDepartureTime(part.depTime);
                walkLeg.setTravelTime(part.travelTime);
                Id<Link> startLinkId = part.fromStop == null ? null : part.fromStop.getLinkId();
                Id<Link> endLinkId =  part.toStop == null ? null : part.toStop.getLinkId();
                Route walkRoute = new GenericRouteImpl(startLinkId, endLinkId);
                walkRoute.setTravelTime(part.travelTime);
                walkRoute.setDistance(part.distance);
                walkLeg.setRoute(walkRoute);
                legs.add(walkLeg);
            }
        }

        return legs;
    }
}
