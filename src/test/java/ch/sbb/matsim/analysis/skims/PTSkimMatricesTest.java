package ch.sbb.matsim.analysis.skims;

import ch.sbb.matsim.analysis.skims.PTSkimMatrices.ODConnection;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.core.utils.misc.Time;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mrieser
 */
public class PTSkimMatricesTest {

    @Test
    public void testSortAndFilterConnections() {
        List<ODConnection> connections = new ArrayList<>();

        // we'll misuse the transferCount as a connection identifier
        // 15-min headway
        connections.add(new ODConnection(Time.parseTime("08:05:00"), 600, 60, 150, 0, null));
        connections.add(new ODConnection(Time.parseTime("08:20:00"), 600, 60, 150, 5, null));
        connections.add(new ODConnection(Time.parseTime("08:35:00"), 600, 60, 150, 3, null));
        connections.add(new ODConnection(Time.parseTime("08:50:00"), 600, 60, 150, 1, null));
        connections.add(new ODConnection(Time.parseTime("09:05:00"), 600, 60, 150, 4, null));

        // two special, fast courses
        connections.add(new ODConnection(Time.parseTime("08:22:00"), 300, 60, 150, 2, null));
        connections.add(new ODConnection(Time.parseTime("08:48:00"), 300, 60, 150, 6, null));

        // randomize the list. instead of randomizing, we sort the connections by transferCount which we misused to specify an order

        connections.sort((c1, c2) -> Double.compare(c1.transferCount, c2.transferCount));

        connections = PTSkimMatrices.RowWorker.sortAndFilterConnections(connections);

        // connection 5 (dep 10900) should be dominated by connection 2 (dep 11000)
        // connection 1 (dep 12700) should be dominated by connection 6 (dep 12600)

        Assert.assertEquals(5, connections.size());
        Assert.assertEquals(0, connections.get(0).transferCount, 0.0);
        Assert.assertEquals(2, connections.get(1).transferCount, 0.0);
        Assert.assertEquals(3, connections.get(2).transferCount, 0.0);
        Assert.assertEquals(6, connections.get(3).transferCount, 0.0);
        Assert.assertEquals(4, connections.get(4).transferCount, 0.0);
    }

    @Test
    public void testCalcAvgAdaptionTime() {
        List<ODConnection> connections = new ArrayList<>();

        // 15-min headway
        connections.add(new ODConnection(Time.parseTime("08:05:00"), 600, 60, 150, 0, null));
        connections.add(new ODConnection(Time.parseTime("08:20:00"), 600, 60, 150, 0, null));
        connections.add(new ODConnection(Time.parseTime("08:35:00"), 600, 60, 150, 0, null));
        connections.add(new ODConnection(Time.parseTime("08:50:00"), 600, 60, 150, 0, null));
        connections.add(new ODConnection(Time.parseTime("09:05:00"), 600, 60, 150, 0, null));

        double adaptionTime = PTSkimMatrices.RowWorker.calcAverageAdaptionTime(connections, Time.parseTime("08:00:00"), Time.parseTime("09:00:00"));
        // there is a departure every 900 seconds, max adaption time would be 450, average of that would be 225.0.
        // it's actually 224 due to sampling errors (we measure every minute, and so we e.g. miss the maximal adaption time of 7.5 minutes, but we measure only 7 minutes twice as maximum
        Assert.assertEquals(224, adaptionTime, 1e-7);
        // the frequency would be 3600 / 224 / 4 = 4.01785

        // two special, fast courses
        connections.add(new ODConnection(Time.parseTime("08:22:00"), 300, 60, 150, 0, null));
        connections.add(new ODConnection(Time.parseTime("08:48:00"), 300, 60, 150, 0, null));

        connections = PTSkimMatrices.RowWorker.sortAndFilterConnections(connections);
        Assert.assertEquals(5, connections.size());

        // there should now be departures at 08:05, 08:22, 08:35, 08:48, 09:05
        // resulting in a slightly higher adaption time

        adaptionTime = PTSkimMatrices.RowWorker.calcAverageAdaptionTime(connections, Time.parseTime("08:00:00"), Time.parseTime("09:00:00"));
        Assert.assertEquals(254, adaptionTime, 1e-7);
        // the frequency would be 3600 / 254 / 4 = 3.5433

        connections.add(new ODConnection(Time.parseTime("08:15:00"), 300, 60, 150, 0, null));

        connections = PTSkimMatrices.RowWorker.sortAndFilterConnections(connections);
        Assert.assertEquals(6, connections.size());

        adaptionTime = PTSkimMatrices.RowWorker.calcAverageAdaptionTime(connections, Time.parseTime("08:00:00"), Time.parseTime("09:00:00"));
        Assert.assertEquals(216, adaptionTime, 1e-7);
        // the frequency would be 3600 / 216 / 4 = 4.1666
    }
}