package ch.sbb.matsim.mobsim.qsim.pt;

import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.mobsim.qsim.components.QSimComponents;
import org.matsim.core.mobsim.qsim.pt.TransitEngineModule;

/**
 * @author Sebastian Hörl / ETHZ
 */
public class SBBTransitEngineQSimModule extends AbstractQSimModule {
	public static final String SBB_TRANSIT_ENGINE = "SBBTransit";
	
	@Override
	protected void configureQSim() {
		bind(SBBTransitQSimEngine.class).asEagerSingleton();
		
		bindMobsimEngine(SBB_TRANSIT_ENGINE).to(SBBTransitQSimEngine.class);
		bindAgentSource(SBB_TRANSIT_ENGINE).to(SBBTransitQSimEngine.class);
		bindDepartureHandler(SBB_TRANSIT_ENGINE).to(SBBTransitQSimEngine.class);
	}
	
	static public void configure(QSimComponents components) {
		// First, remove the standard transit engine
		components.activeAgentSources.remove(TransitEngineModule.TRANSIT_ENGINE_NAME);
		components.activeMobsimEngines.remove(TransitEngineModule.TRANSIT_ENGINE_NAME);
		components.activeDepartureHandlers.remove(TransitEngineModule.TRANSIT_ENGINE_NAME);
		
		// Second, activate SBB transit engine
		components.activeAgentSources.add(SBB_TRANSIT_ENGINE);
		components.activeMobsimEngines.add(SBB_TRANSIT_ENGINE);
		components.activeDepartureHandlers.add(SBB_TRANSIT_ENGINE);
	}
}
