/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

/**
 * @author mrieser / SBB
 */
public class SBBTransitConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBPt";

    static private final String PARAM_DETERMINISTIC_SERVICE_MODES = "deterministicServiceModes";
    static private final String PARAM_CREATE_LINK_EVENTS = "createLinkEvents";

    private Set<String> deterministicServiceModes = new HashSet<>();
    private boolean createLinkEvents = false;

    public SBBTransitConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(PARAM_DETERMINISTIC_SERVICE_MODES)
    private String getDeterministicServiceModesAsString() {
        return CollectionUtils.setToString(this.deterministicServiceModes);
    }

    public Set<String> getDeterministicServiceModes() {
        return this.deterministicServiceModes;
    }

    @StringSetter(PARAM_DETERMINISTIC_SERVICE_MODES)
    private void setDeterministicServiceModes(String modes) {
        setDeterministicServiceModes(CollectionUtils.stringToSet(modes));
    }

    public void setDeterministicServiceModes(Set<String> modes) {
        this.deterministicServiceModes.clear();
        this.deterministicServiceModes.addAll(modes);
    }

    @StringGetter(PARAM_CREATE_LINK_EVENTS)
    private String getCreateLinkEventsAsString() {
        return Boolean.toString(this.createLinkEvents);
    }

    public boolean isCreateLinkEvents() {
        return this.createLinkEvents;
    }

    @StringSetter(PARAM_CREATE_LINK_EVENTS)
    private void setCreateLinkEvents(String value) {
        this.createLinkEvents = Boolean.parseBoolean(value);
    }

    public void setCreateLinkEvents(boolean value) {
        this.createLinkEvents = value;
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_DETERMINISTIC_SERVICE_MODES, "Leg modes used by the created transit drivers that should be simulated strictly according to the schedule.");
        comments.put(PARAM_CREATE_LINK_EVENTS, "Specifies whether the deterministic simulation should create linkEnter- and linkLeave-events, useful for visualization purposes. Defaults to false.");
        return comments;
    }
}
