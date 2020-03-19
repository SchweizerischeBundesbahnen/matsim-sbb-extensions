package ch.sbb.matsim.routing.graph;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Time;
import org.matsim.vehicles.Vehicle;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Implements a least-cost-path-tree upon a {@link Graph} datastructure.
 * Besides using the more efficient Graph datastructure, it also makes
 * use of a custom priority-queue implementation (NodeMinHeap) which
 * operates directly on the least-cost-path-three data for additional
 * performance gains.
 *
 * In some limited tests, this resulted in a speed-up of at least a
 * factor 2.5 compared to MATSim's default LeastCostPathTree.
 *
 * The implementation does not allocate any memory in the
 * {@link #calculate(int, double, Person, Vehicle)} method. All required
 * memory is pre-allocated in the constructor. This makes the implementation
 * NOT thread-safe.
 */
public class LeastCostPathTree {

    private final Graph graph;
    private final TravelTime tt;
    private final TravelDisutility td;
    private final double[] data; // 3 entries per node: time, cost, distance
    private final int[] comingFrom;
    private final Graph.LinkIterator outLI;
    private final NodeMinHeap pq;

    public LeastCostPathTree(Graph graph, TravelTime tt, TravelDisutility td) {
        this.graph = graph;
        this.tt = tt;
        this.td = td;
        this.data = new double[graph.nodeCount * 3];
        this.comingFrom = new int[graph.nodeCount];
        this.pq = new NodeMinHeap();
        this.outLI = graph.getOutLinkIterator();
    }

    public void calculate(int startNode, double startTime, Person person, Vehicle vehicle) {
        Arrays.fill(this.data, Double.POSITIVE_INFINITY);
        Arrays.fill(this.comingFrom, -1);

        setData(startNode, 0, startTime, 0);

        this.pq.clear();
        this.pq.insert(startNode);

        while (!pq.isEmpty()) {
            final int nodeIdx = pq.poll();
            double currTime = getTime(nodeIdx);
            double currCost = getCost(nodeIdx);
            double currDistance = getDistance(nodeIdx);
            outLI.reset(nodeIdx);
            while (outLI.next()) {
                int linkIdx = outLI.getLinkIndex();
                Link link = this.graph.getLink(linkIdx);
                int toNode = outLI.getToNodeIndex();

                double travelTime = this.tt.getLinkTravelTime(link, currTime, person, vehicle);
                double newTime = currTime + travelTime;
                double newCost = currCost + this.td.getLinkTravelDisutility(link, currTime, person, vehicle);

                double oldCost = getCost(toNode);
                if (Double.isFinite(oldCost)) {
                    if (newCost < oldCost) {
                        pq.decreaseKey(toNode, newCost);
                        setData(toNode, newCost, newTime, currDistance + link.getLength());
                        this.comingFrom[toNode] = nodeIdx;
                    }
                } else {
                    setData(toNode, newCost, newTime, currDistance + link.getLength());
                    pq.insert(toNode);
                    this.comingFrom[toNode] = nodeIdx;
                }
            }
        }
    }

    public double getCost(int nodeIndex) {
        return this.data[nodeIndex * 3];
    }

    public double getTime(int nodeIndex) {
        double time = this.data[nodeIndex * 3 + 1];
        if (Double.isInfinite(time)) {
            return Time.getUndefinedTime();
        }
        return time;
    }

    public double getDistance(int nodeIndex) {
        return this.data[nodeIndex * 3 + 2];
    }

    private void setCost(int nodeIndex, double cost) {
        this.data[nodeIndex * 3] = cost;
    }

    private void setData(int nodeIndex, double cost, double time, double distance) {
        int index = nodeIndex * 3;
        this.data[index] = cost;
        this.data[index + 1] = time;
        this.data[index + 2] = distance;
    }

    public int getComingFrom(int nodeIndex) {
        return this.comingFrom[nodeIndex];
    }

    private class NodeMinHeap {
        private final int heap[];
        private int size = 0;

        NodeMinHeap() {
            this.heap = new int[graph.nodeCount]; // worst case: every node is part of the heap
        }

        void insert(int node) {
            int i = this.size;
            heap[i] = node;
            this.size++;

            int parent = parent(i);

            while (parent != i && getCost(heap[i]) < getCost(heap[parent])) {
                swap(i, parent);
                i = parent;
                parent = parent(i);
            }
        }

        void decreaseKey(int node, double cost) {
            int i;
            for (i = 0; i < size; i++) {
                if (this.heap[i] == node) {
                    break;
                }
            }
            if (getCost(heap[i]) < cost) {
                throw new IllegalArgumentException("existing cost is already smaller than new cost.");
            }

            setCost(node, cost);
            int parent = parent(i);

            // sift up
            while (i > 0 && getCost(heap[parent]) > getCost(heap[i])) {
                swap(i, parent);
                i = parent;
                parent = parent(parent);
            }
        }

        int poll() {
            if (this.size == 0) {
                throw new NoSuchElementException("heap is empty");
            }
            if (this.size == 1) {
                this.size--;
                return this.heap[0];
            }

            int root = this.heap[0];

            // remove the last item, set it as new root
            int lastNode = this.heap[this.size - 1];
            this.size--;
            this.heap[0] = lastNode;

            // sift down
            minHeapify(0);

            return root;
        }

        int peek() {
            if (this.size == 0) {
                throw new NoSuchElementException("heap is empty");
            }
            return this.heap[0];
        }

        int size() {
            return this.size;
        }

        boolean isEmpty() {
            return this.size == 0;
        }

        void clear() {
            this.size = 0;
        }

        private void minHeapify(int i) {
            int left = left(i);
            int right = right(i);
            int smallest = i;

            if (left <= (size - 1) && getCost(heap[left]) < getCost(heap[i])) {
                smallest = left;
            }
            if (right <= (size - 1) && getCost(heap[right]) < getCost(heap[smallest])) {
                smallest = right;
            }
            if (smallest != i) {
                swap(i, smallest);
                minHeapify(smallest);
            }
        }

        private int right(int i) {
            return 2 * i + 2;
        }

        private int left(int i) {
            return 2 * i + 1;
        }

        private int parent(int i) {
            return (i - 1) / 2;
        }

        private void swap(int i, int parent) {
            int tmp = this.heap[parent];
            this.heap[parent] = this.heap[i];
            this.heap[i] = tmp;
        }
    }
}
