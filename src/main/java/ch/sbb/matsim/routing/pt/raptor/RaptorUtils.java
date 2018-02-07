/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.core.config.Config;
import org.matsim.pt.router.TransitRouterConfig;

/**
 * @author mrieser / SBB
 */
public final class RaptorUtils {

    private RaptorUtils() {
    }

    public static RaptorConfig createRaptorConfig(Config config) {
        TransitRouterConfig trConfig = new TransitRouterConfig(config);
        RaptorConfig raptorConfig = new RaptorConfig();
        raptorConfig.setBeelineWalkConnectionDistance(trConfig.getBeelineWalkConnectionDistance());
        raptorConfig.setBeelineWalkSpeed(trConfig.getBeelineWalkSpeed());

        raptorConfig.setSearchRadius(trConfig.getSearchRadius());
        raptorConfig.setExtensionRadius(trConfig.getExtensionRadius());

        raptorConfig.setMinimalTransferTime(trConfig.getAdditionalTransferTime());

        raptorConfig.setMarginalUtilityOfTravelTimeWalk_utl_s(trConfig.getMarginalUtilityOfTravelTimeWalk_utl_s());
        raptorConfig.setMarginalUtilityOfTravelTimeAccessWalk_utl_s(trConfig.getMarginalUtilityOfTravelTimeWalk_utl_s());
        raptorConfig.setMarginalUtilityOfTravelTimeEgressWalk_utl_s(trConfig.getMarginalUtilityOfTravelTimeWalk_utl_s());
        raptorConfig.setMarginalUtilityOfTravelTimePt_utl_s(trConfig.getMarginalUtilityOfTravelTimePt_utl_s());
        raptorConfig.setMarginalUtilityOfWaitingPt_utl_s(trConfig.getMarginalUtilityOfWaitingPt_utl_s());

        raptorConfig.setTransferPenaltyCost(-trConfig.getUtilityOfLineSwitch_utl());

        return raptorConfig;
    }
}
