/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

/**
 * @author mrieser / SBB
 */
public class RaptorConfig {

    /**
     * The distance in meters in which stop facilities should be searched for
     * around the start and end coordinate.
     */
    private double searchRadius = 1000.0;

    /**
     * If no stop facility is found around start or end coordinate (see
     * {@link #searchRadius}), the nearest stop location is searched for
     * and the distance from start/end coordinate to this location is
     * extended by the given amount.<br />
     * If only one stop facility is found within {@link #searchRadius},
     * the radius is also extended in the hope to find more stop
     * facilities (e.g. in the opposite direction of the already found
     * stop).
     */
    private double extensionRadius = 200.0;

    /**
     * The distance in meters that agents can walk to get from one stop to
     * another stop of a nearby transit line.
     */
    private double beelineWalkConnectionDistance = 200.0;

    private double beelineWalkSpeed; // meter / second

    private double marginalUtilityOfTravelTimeWalk_utl_s;

    private double marginalUtilityOfTravelTimeAccessWalk_utl_s;

    private double marginalUtilityOfTravelTimeEgressWalk_utl_s;

    private double marginalUtilityOfTravelTimePt_utl_s;

    private double marginalUtilityOfWaitingPt_utl_s;

    private double minimalTransferTime = 60;

    private double transferPenaltyCost = 0;


    public double getSearchRadius() {
        return this.searchRadius;
    }

    public void setSearchRadius(double searchRadius) {
        this.searchRadius = searchRadius;
    }

    public double getExtensionRadius() {
        return this.extensionRadius;
    }

    public void setExtensionRadius(double extensionRadius) {
        this.extensionRadius = extensionRadius;
    }

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

    public double getMarginalUtilityOfTravelTimePt_utl_s() {
        return this.marginalUtilityOfTravelTimePt_utl_s;
    }

    public void setMarginalUtilityOfTravelTimePt_utl_s(double marginalUtilityOfTravelTimePt_utl_s) {
        this.marginalUtilityOfTravelTimePt_utl_s = marginalUtilityOfTravelTimePt_utl_s;
    }

    public double getMarginalUtilityOfWaitingPt_utl_s() {
        return this.marginalUtilityOfWaitingPt_utl_s;
    }

    public void setMarginalUtilityOfWaitingPt_utl_s(double marginalUtilityOfWaitingPt_utl_s) {
        this.marginalUtilityOfWaitingPt_utl_s = marginalUtilityOfWaitingPt_utl_s;
    }

    public double getMinimalTransferTime() {
        return this.minimalTransferTime;
    }

    public void setMinimalTransferTime(double minimalTransferTime) {
        this.minimalTransferTime = minimalTransferTime;
    }

    public double getTransferPenaltyCost() {
        return this.transferPenaltyCost;
    }

    public void setTransferPenaltyCost(double transferPenaltyCost) {
        this.transferPenaltyCost = transferPenaltyCost;
    }
}
