/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Static configuration of SwissRailRaptor used to initialize it.
 * These values are only used initially to build the necessary dataset for SwissRailRaptor
 * (see {@link SwissRailRaptorData}. Once initialized, changes to values in this class
 * will have no effect.
 *
 * @author mrieser / SBB
 */
public class RaptorStaticConfig {

    public enum RaptorOptimization {
        /**
         * Use this option if you plan to calculate simple from-to routes
         * (see {@link SwissRailRaptor#calcRoute(org.matsim.facilities.Facility, org.matsim.facilities.Facility, double, org.matsim.api.core.v01.population.Person)}).
         */
        OneToOneRouting,
        /**
         * Use this option if you plan to calculate one-to-all least-cost-path-trees
         * (see {@link SwissRailRaptor#calcTree(org.matsim.pt.transitSchedule.api.TransitStopFacility, double, RaptorParameters)}).
         */
        OneToAllRouting }

    /**
     * The distance in meters that agents can walk to get from one stop to
     * another stop of a nearby transit line.
     */
    private double beelineWalkConnectionDistance = 200.0;
    private double beelineWalkSpeed; // meter / second
    private double marginalUtilityOfTravelTimeWalk_utl_s;
    private double marginalUtilityOfTravelTimeAccessWalk_utl_s;
    private double marginalUtilityOfTravelTimeEgressWalk_utl_s;

    private double minimalTransferTime = 60;

    private boolean useModeMappingForPassengers = false;
    private final Map<String, String> passengerModeMappings = new HashMap<>();

    private RaptorOptimization optimization = RaptorOptimization.OneToOneRouting;

    public double getBeelineWalkConnectionDistance() {
        return this.beelineWalkConnectionDistance;
    }

    public void setBeelineWalkConnectionDistance(double beelineWalkConnectionDistance) {
        this.beelineWalkConnectionDistance = beelineWalkConnectionDistance;
    }

    public double getBeelineWalkSpeed() {
        return this.beelineWalkSpeed;
    }

    public void setBeelineWalkSpeed(double beelineWalkSpeed) {
        this.beelineWalkSpeed = beelineWalkSpeed;
    }

    public double getMarginalUtilityOfTravelTimeWalk_utl_s() {
        return this.marginalUtilityOfTravelTimeWalk_utl_s;
    }

    public void setMarginalUtilityOfTravelTimeWalk_utl_s(double marginalUtilityOfTravelTimeWalk_utl_s) {
        this.marginalUtilityOfTravelTimeWalk_utl_s = marginalUtilityOfTravelTimeWalk_utl_s;
    }

    public double getMarginalUtilityOfTravelTimeAccessWalk_utl_s() {
        return this.marginalUtilityOfTravelTimeAccessWalk_utl_s;
    }

    public void setMarginalUtilityOfTravelTimeAccessWalk_utl_s(double marginalUtilityOfTravelTimeAccessWalk_utl_s) {
        this.marginalUtilityOfTravelTimeAccessWalk_utl_s = marginalUtilityOfTravelTimeAccessWalk_utl_s;
    }

    public double getMarginalUtilityOfTravelTimeEgressWalk_utl_s() {
        return this.marginalUtilityOfTravelTimeEgressWalk_utl_s;
    }

    public void setMarginalUtilityOfTravelTimeEgressWalk_utl_s(double marginalUtilityOfTravelTimeEgressWalk_utl_s) {
        this.marginalUtilityOfTravelTimeEgressWalk_utl_s = marginalUtilityOfTravelTimeEgressWalk_utl_s;
    }

    public double getMinimalTransferTime() {
        return this.minimalTransferTime;
    }

    public void setMinimalTransferTime(double minimalTransferTime) {
        this.minimalTransferTime = minimalTransferTime;
    }

    public boolean isUseModeMappingForPassengers() {
        return this.useModeMappingForPassengers;
    }

    public void setUseModeMappingForPassengers(boolean useModeMappingForPassengers) {
        this.useModeMappingForPassengers = useModeMappingForPassengers;
    }

    public void addModeMappingForPassengers(String routeMode, String passengerMode) {
        this.passengerModeMappings.put(routeMode, passengerMode);
    }

    public String getPassengerMode(String routeMode) {
        return this.passengerModeMappings.get(routeMode);
    }

    public RaptorOptimization getOptimization() {
        return this.optimization;
    }

    public void setOptimization(RaptorOptimization optimization) {
        this.optimization = optimization;
    }
}
