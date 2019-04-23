/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.skims;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.opengis.feature.simple.SimpleFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Calculates zone-to-zone matrices containing a number of performance indicators related to modes routed on a network.
 *
 * Inspired by https://github.com/moeckel/silo/blob/siloMatsim/silo/src/main/java/edu/umd/ncsg/transportModel/Zone2ZoneTravelTimeListener.java.
 *
 * Idea of the algorithm:
 * - given n points per zone
 * - find the nearest link and thereof the to-node for each point
 * - this results in n nodes per zone (where some nodes can appear multiple times, this is wanted as it acts as a weight/probability)
 * - for each zone-to-zone combination, calculate the travel times for each node to node combination.
 * - this results in n x n travel times per zone-to-zone combination.
 * - average the n x n travel times and store this value as the zone-to-zone travel time.
 *
 * @author mrieser / SBB
 */
public final class NetworkSkimMatrices {

    private NetworkSkimMatrices() {
    }

    public static <T> NetworkIndicators<T> calculateSkimMatrices(Network xy2lNetwork, Network routingNetwork, Map<T, SimpleFeature> zones, Map<T, Coord[]> coordsPerZone, double departureTime, TravelTime travelTime, TravelDisutility travelDisutility, int numberOfThreads) {
        Map<T, Node[]> nodesPerZone = new HashMap<>();
        for (Map.Entry<T, Coord[]> e : coordsPerZone.entrySet()) {
            T zoneId = e.getKey();
            Coord[] coords = e.getValue();
            Node[] nodes = new Node[coords.length];
            nodesPerZone.put(zoneId, nodes);
            for (int i = 0; i < coords.length; i++) {
                Coord coord = coords[i];
                Node node = NetworkUtils.getNearestLink(xy2lNetwork, coord).getToNode();
                nodes[i] = node;
            }
        }

        // prepare calculation
        NetworkIndicators<T> networkIndicators = new NetworkIndicators<>(zones.keySet());

        int numberOfPointsPerZone = coordsPerZone.values().iterator().next().length;
        float avgFactor = (float) (1.0 / numberOfPointsPerZone / numberOfPointsPerZone);

        // do calculation
        ConcurrentLinkedQueue<T> originZones = new ConcurrentLinkedQueue<>(zones.keySet());

        Counter counter = new Counter("CAR-TravelTimeMatrix-" + Time.writeTime(departureTime) + " zone ", " / " + zones.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RowWorker<T> worker = new RowWorker<>(originZones, zones.keySet(), routingNetwork, nodesPerZone, networkIndicators, departureTime, travelTime, travelDisutility, counter);
            threads[i] = new Thread(worker, "CAR-TravelTimeMatrix-" + Time.writeTime(departureTime) + "-" + i);
            threads[i].start();
        }

        // wait until all threads have finished
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        networkIndicators.travelTimeMatrix.multiply(avgFactor);
        networkIndicators.distanceMatrix.multiply(avgFactor);

        return networkIndicators;
    }

    private static class RowWorker<T> implements Runnable {
        private final ConcurrentLinkedQueue<T> originZones;
        private final Set<T> destinationZones;
        private final Network network;
        private final Map<T, Node[]> nodesPerZone;
        private final NetworkIndicators<T> networkIndicators;
        private final TravelTime travelTime;
        private final TravelDisutility travelDisutility;
        private final double departureTime;
        private final Counter counter;

        RowWorker(ConcurrentLinkedQueue<T> originZones, Set<T> destinationZones, Network network, Map<T, Node[]> nodesPerZone, NetworkIndicators<T> networkIndicators, double departureTime, TravelTime travelTime, TravelDisutility travelDisutility, Counter counter) {
            this.originZones = originZones;
            this.destinationZones = destinationZones;
            this.network = network;
            this.nodesPerZone = nodesPerZone;
            this.networkIndicators = networkIndicators;
            this.departureTime = departureTime;
            this.travelTime = travelTime;
            this.travelDisutility = travelDisutility;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree lcpTree = new LeastCostPathTree(this.travelTime, this.travelDisutility);
            while (true) {
                T fromZoneId = this.originZones.poll();
                if (fromZoneId == null) {
                    return;
                }

                this.counter.incCounter();
                Node[] fromNodes = this.nodesPerZone.get(fromZoneId);
                if (fromNodes != null) {
                    for (Node fromNode : fromNodes) {
                        lcpTree.calculate(this.network, fromNode, this.departureTime);

                        for (T toZoneId : this.destinationZones) {
                            Node[] toNodes = this.nodesPerZone.get(toZoneId);
                            if (toNodes != null) {
                                for (Node toNode : toNodes) {
                                    LeastCostPathTree.NodeData data = lcpTree.getTree().get(toNode.getId());
                                    double tt = data.getTime() - this.departureTime;
                                    double dist = data.getDistance();
                                    this.networkIndicators.travelTimeMatrix.add(fromZoneId, toZoneId, (float) tt);
                                    this.networkIndicators.distanceMatrix.add(fromZoneId, toZoneId, (float) dist);
                                }
                            } else {
                                // this might happen if a zone has no geometry, for whatever reason...
                                this.networkIndicators.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                                this.networkIndicators.distanceMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                            }
                        }
                    }
                } else {
                    // this might happen if a zone has no geometry, for whatever reason...
                    for (T toZoneId : this.destinationZones) {
                        this.networkIndicators.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                        this.networkIndicators.distanceMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                    }
                }
            }
        }
    }

    static class NetworkIndicators<T> {
        final FloatMatrix<T> travelTimeMatrix;
        final FloatMatrix<T> distanceMatrix;

        NetworkIndicators(Set<T> zones) {
            this.travelTimeMatrix = new FloatMatrix<>(zones, 0);
            this.distanceMatrix = new FloatMatrix<>(zones, 0);
        }
    }


}
