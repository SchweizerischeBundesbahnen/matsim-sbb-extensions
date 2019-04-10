/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.skims;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.misc.Time;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Calculates a least-cost-path tree using Dijkstra's algorithm  for calculating a shortest-path
 * tree, given a node as root of the tree.
 *
 * THIS IS A COPY of org.matsim.utils.leastcostpathtree.LeastCostPathTree,
 * modified such that not only travel time and travel cost, but also distance is calculated.
 * (modification by mrieser/SBB)
 *
 * 
 * @author balmermi, mrieser
 * @author mrieser / SBB
 */
public class LeastCostPathTree {

	// ////////////////////////////////////////////////////////////////////
	// member variables
	// ////////////////////////////////////////////////////////////////////

	private Node origin1 = null;
	private double dTime = Time.getUndefinedTime();
	
	private final TravelTime ttFunction;
	private final TravelDisutility tcFunction;
	private HashMap<Id<Node>, NodeData> nodeData = null;
	
	private final static Vehicle VEHICLE = VehicleUtils.getFactory().createVehicle(Id.create("theVehicle", Vehicle.class), VehicleUtils.getDefaultVehicleType());
	private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

	// ////////////////////////////////////////////////////////////////////
	// constructors
	// ////////////////////////////////////////////////////////////////////

	public LeastCostPathTree(TravelTime tt, TravelDisutility tc) {
		this.ttFunction = tt;
		this.tcFunction = tc;
	}

	public void calculate(final Network network, final Node origin, final double time) {
		this.origin1 = origin;
		this.dTime = time;
		
		this.nodeData = new HashMap<Id<Node>, NodeData>((int) (network.getNodes().size() * 1.1), 0.95f);
		NodeData d = new NodeData();
		d.time = time;
		d.cost = 0;
		this.nodeData.put(origin.getId(), d);

		ComparatorCost comparator = new ComparatorCost(this.nodeData);
		PriorityQueue<Node> pendingNodes = new PriorityQueue<Node>(500, comparator);
		relaxNode(origin, pendingNodes);
		while (!pendingNodes.isEmpty()) {
			Node n = pendingNodes.poll();
			relaxNode(n, pendingNodes);
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// inner classes
	// ////////////////////////////////////////////////////////////////////

	public static class NodeData {
		private Id<Node> prevId = null;
		private double cost = Double.MAX_VALUE;
		private double time = 0;
		private double distance = 0;

        /*package*/ void visit(final Id<Node> comingFromNodeId, final double cost1, final double time1, double distance) {
			this.prevId = comingFromNodeId;
			this.cost = cost1;
			this.time = time1;
			this.distance = distance;
		}

		public double getCost() {
			return this.cost;
		}

		public double getTime() {
			return this.time;
		}

		public double getDistance() {
			return this.distance;
		}

		public Id<Node> getPrevNodeId() {
			return this.prevId;
		}
	}

	/*package*/ static class ComparatorCost implements Comparator<Node> {
		protected Map<Id<Node>, ? extends NodeData> nodeData;

		ComparatorCost(final Map<Id<Node>, ? extends NodeData> nodeData) {
			this.nodeData = nodeData;
		}

		@Override
		public int compare(final Node n1, final Node n2) {
			double c1 = getCost(n1);
			double c2 = getCost(n2);
			if (c1 < c2)
				return -1;
			if (c1 > c2)
				return +1;
			return n1.getId().compareTo(n2.getId());
		}

		protected double getCost(final Node node) {
			return this.nodeData.get(node.getId()).getCost();
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// get methods
	// ////////////////////////////////////////////////////////////////////

	public final Map<Id<Node>, NodeData> getTree() {
		return this.nodeData;
	}

	/**
	 * @return Returns the root of the calculated tree, or <code>null</code> if no tree was calculated yet.
	 */
	public final Node getOrigin() {
		return this.origin1;
	}

	public final double getDepartureTime() {
		return this.dTime;
	}

	// ////////////////////////////////////////////////////////////////////
	// private methods
	// ////////////////////////////////////////////////////////////////////

	private void relaxNode(final Node n, PriorityQueue<Node> pendingNodes) {
		NodeData nData = nodeData.get(n.getId());
		double currTime = nData.getTime();
		double currCost = nData.getCost();
		double currDistance = nData.distance;
		for (Link l : n.getOutLinks().values()) {
			Node nn = l.getToNode();
			NodeData nnData = nodeData.get(nn.getId());
			if (nnData == null) {
				nnData = new NodeData();
				this.nodeData.put(nn.getId(), nnData);
			}
			double visitCost = currCost + tcFunction.getLinkTravelDisutility(l, currTime, PERSON, VEHICLE);
			double visitTime = currTime + ttFunction.getLinkTravelTime(l, currTime, PERSON, VEHICLE);
			double distance = currDistance + l.getLength();

			if (visitCost < nnData.getCost()) {
				pendingNodes.remove(nn);
				nnData.visit(n.getId(), visitCost, visitTime, distance);
				additionalComputationsHook( l, currTime ) ;
				pendingNodes.add(nn);
			}
		}
	}

	protected void additionalComputationsHook( Link link, double currTime ) {
		// left empty for inheritance
	}

	// ////////////////////////////////////////////////////////////////////
	// main method
	// ////////////////////////////////////////////////////////////////////

	public static void main(String[] args) {
		MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Network network = scenario.getNetwork();
		new MatsimNetworkReader(scenario.getNetwork()).readFile("../../input/network.xml");

		TravelTimeCalculator ttc = new TravelTimeCalculator(network, 60, 30 * 3600, scenario.getConfig().travelTimeCalculator());
		LeastCostPathTree st = new LeastCostPathTree(ttc.getLinkTravelTimes(), new RandomizingTimeDistanceTravelDisutilityFactory( TransportMode.car, scenario.getConfig().planCalcScore() ).createTravelDisutility(ttc.getLinkTravelTimes()));
		Node origin = network.getNodes().get(Id.create(1, Node.class));
		st.calculate(network, origin, 8*3600);
		Map<Id<Node>, NodeData> tree = st.getTree();
		for (Map.Entry<Id<Node>, NodeData> e : tree.entrySet()) {
			Id<Node> id = e.getKey();
			NodeData d = e.getValue();
			if (d.getPrevNodeId() != null) {
				System.out.println(id + "\t" + d.getTime() + "\t" + d.getCost() + "\t" + d.getPrevNodeId());
			} else {
				System.out.println(id + "\t" + d.getTime() + "\t" + d.getCost() + "\t" + "0");
			}
		}
	}
}
