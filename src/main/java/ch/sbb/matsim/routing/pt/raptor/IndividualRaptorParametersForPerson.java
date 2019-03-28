package ch.sbb.matsim.routing.pt.raptor;

import java.util.Arrays;
import java.util.Map;

import javax.inject.Inject;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

/**
 * An implementation of {@link RaptorParametersForPerson} that returns an
 * individual set of routing parameters, based on
 * {@link ScoringParametersForPerson}.
 *
 * @author sebhoerl / ETHZ
 */
public class IndividualRaptorParametersForPerson implements RaptorParametersForPerson {
	private final Config config;
	private final ScoringParametersForPerson parametersForPerson;

	@Inject
	public IndividualRaptorParametersForPerson(Config config, ScoringParametersForPerson parametersForPerson) {
		this.config = config;
		this.parametersForPerson = parametersForPerson;
	}

	@Override
	public RaptorParameters getRaptorParameters(Person person) {
		RaptorParameters raptorParameters = RaptorUtils.createParameters(config);
		ScoringParameters scoringParameters = parametersForPerson.getScoringParameters(person);

		double marginalUtilityOfPerforming = scoringParameters.marginalUtilityOfPerforming_s;

		raptorParameters.setMarginalUtilityOfWaitingPt_utl_s(
				scoringParameters.marginalUtilityOfWaitingPt_s - marginalUtilityOfPerforming);

		PlanCalcScoreConfigGroup pcsConfig = config.planCalcScore();

		for (Map.Entry<String, PlanCalcScoreConfigGroup.ModeParams> e : pcsConfig.getModes().entrySet()) {
			String mode = e.getKey();
			ModeUtilityParameters modeParams = scoringParameters.modeParams.get(mode);

			if (modeParams != null) {
				raptorParameters.setMarginalUtilityOfTravelTime_utl_s(mode,
						modeParams.marginalUtilityOfTraveling_s - marginalUtilityOfPerforming);
			}
		}

		ModeUtilityParameters walkParams = scoringParameters.modeParams.get(TransportMode.walk);

		for (String fallbackMode : Arrays.asList(TransportMode.access_walk, TransportMode.egress_walk,
				TransportMode.transit_walk)) {
			ModeUtilityParameters modeParams = scoringParameters.modeParams.get(fallbackMode);

			if (modeParams != null) {
				raptorParameters.setMarginalUtilityOfTravelTime_utl_s(fallbackMode,
						walkParams.marginalUtilityOfTraveling_s - marginalUtilityOfPerforming);
			}
		}

		raptorParameters.setTransferPenaltyFixCostPerTransfer(-scoringParameters.utilityOfLineSwitch);

		return raptorParameters;
	}
}
