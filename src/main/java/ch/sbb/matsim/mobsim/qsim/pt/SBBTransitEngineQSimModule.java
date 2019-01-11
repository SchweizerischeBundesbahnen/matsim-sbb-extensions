package ch.sbb.matsim.mobsim.qsim.pt;

import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfig;
import org.matsim.core.mobsim.qsim.pt.ComplexTransitStopHandlerFactory;
import org.matsim.core.mobsim.qsim.pt.TransitEngineModule;
import org.matsim.core.mobsim.qsim.pt.TransitStopHandlerFactory;

/**
 * @author Sebastian Hörl / ETHZ
 */
public class SBBTransitEngineQSimModule extends AbstractQSimModule {
	public static final String COMPONENT_NAME = "SBBTransit";
	
	@Override
	protected void configureQSim() {
		bind(SBBTransitQSimEngine.class).asEagerSingleton();
		addNamedComponent(SBBTransitQSimEngine.class, COMPONENT_NAME);
		bind(TransitStopHandlerFactory.class).to(ComplexTransitStopHandlerFactory.class);
	}
	
	static public void configure(QSimComponentsConfig components) {
		if (components.hasNamedComponent(TransitEngineModule.TRANSIT_ENGINE_NAME)) {
			components.removeNamedComponent(TransitEngineModule.TRANSIT_ENGINE_NAME);
		}
		
		components.addNamedComponent(COMPONENT_NAME);
	}
}
