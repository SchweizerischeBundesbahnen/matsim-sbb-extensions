# MATSim-Extensions by SBB [![](https://jitpack.io/v/SchweizerischeBundesbahnen/matsim-sbb-extensions.svg)](https://jitpack.io/#SchweizerischeBundesbahnen/matsim-sbb-extensions)

The following extensions for [MATSim](http://www.matsim.org/) are provided by
the [Swiss Federal Railways](http://www.sbb.ch/) (SBB, Schweizerische Bundesbahnen):

- [SwissRailRaptor](#swissRailRaptor)
- [Deterministic PT Simulation](#detPTSim)

To use the extensions along your MATSim code, follow these two steps:

 **Step 1**. Add the JitPack repository to your `pom.xml`.
  ```$xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
  ```
  **Step 2**. Add the dependency
   ```$xml
	<dependency>
	    <groupId>com.github.SchweizerischeBundesbahnen</groupId>
	    <artifactId>matsim-sbb-extensions</artifactId>
	    <version>0.9.x</version>
	</dependency>
  ``` 

## SwissRailRaptor <span id="swissRailRaptor" />

The SwissRailRaptor is a fast public transport router. It is based on the RAPTOR algorithm
(Delling et al, 2012, Round-Based Public Transit Routing), and applies several optimizations,
namely in the selection which transfers need to be added and which transfers can be left out
without influencing the outcome of the routing-process.

Actual performance gains vary by scenario, but are typically in the order of one to two magnitudes
compared to the default pt router in MATSim.
When applied to the complete public transport schedule of Switzerland (including trains, buses, trams,
ships, cable-cars, ...), SwissRailRaptor was 95 times faster than MATSim's default pt router.
In smaller scenarios, SwissRailRaptor was measured to be between 20 - 30 times faster.
Memory consumption of SwissRailRaptor should also be at least one magnitude lower when compared to
MATSim's default router, as should be the pre-processing time to initialize the router.

SwissRailRaptor can act as a drop-in replacement for the pt router included in MATSim by default
when it is used without further configuration, re-using the configuration parameters
from the default `transitRouter` config group. A special config group is available that allows to 
configure advanced features of the SwissRailRaptor not available in MATSim's default pt router (see
below).

A major difference to the default transit router in MATSim is the fact that SwissRailRaptor 
does not repeat the transit schedule after 24 hours when searching for a route,
but only takes the actual departure times as specified in the schedule into account. This is due
to the fact that not all schedules have a periodicity of 24 hours. When applied in MATSim, it can result
in agents no longer finding a route when departing late at night. Considering such agents would have
gotten stuck in the simulation anyway due to no scheduled pt vehicles running at that time 
the next day, this should not pose any real problem.

Have a look at the class `ch.sbb.matsim.RunSBBExtension` included in the repository to see 
how to enable SwissRailRaptor when running MATSim.

### Configuration of Advanced Features

Besides acting as a drop-in replacement for MATSim's default pt router, SwissRailRaptor provides
additional features that need special configuration to be activated.

#### Intermodal Access and Egress

By default, all legs leading from the start coordinate to the first transit stop, or leading from
the last transit stop to the destination coordinate, are assumed to be undertaken by walking.
But SwissRailRaptor also support choosing different modes for these access and egress legs.

Other modes, e.g. bike, usually have a higher speed, and thus transit stops with a larger distance
to the start or destination coordinate should be taken into account than just those reachable
by a sensible walking duration. In order to reduce the number of potential start and destination
stops when increasing the search radius, SwissRailRouter allows to filter the stops based on
stops' attributes.

To use intermodal access and egress legs and configure the allowed modes and stops, add
the following config module to your `config.xml`:

  ```$xml
  <module name="swissRailRaptor">
    <param name="useIntermodalAccessEgress" value="true" />
    
    <paramset type="intermodalAccessEgress">
      <param name="mode" value="walk" />
      <param name="radius" value="1000" />
      <param name="subpopulations" value="" /> <!-- an empty value applies to every agent, comma-separated list of multiple subpopulations possible -->
    </paramset>
    <paramset type="intermodalAccessEgress">
      <param name="mode" value="bike" />
      <param name="radius" value="3000" />
      <param name="subpopulations" value="cyclists,bikers" />
      <param name="linkIdAttribute" value="accessLinkId_bike" />
      <param name="stopFilterAttribute" value="bikeAccessible" />
      <param name="stopFilterValue" value="true" />
    </paramset>
  </module>
  ```
In the above example, intermodal access and egress is enabled (`useIntermodalAccessEgress=true`)
and two modes are configured for it: `walk` and `bike`. Walk can be used by all agents 
(`subpopulation=null`) and uses all transit stops (no `stopFilterAttribute` defined) within a radius 
of 1000 around the start or destination coordinates. Bike can only be used by agents in the 
subpopulation `cyclists`, and uses only transit stops that have an attribute named 
`bikeAccessible` with the value `true`. If bike is routed on the network, it's possible that
no route can be calculated from an activity's link to the transit stop links, e.g. if the transit
stop is a train station and the assigned link refers to a "rail"-link which is not connected to 
the bike-network. In such cases, a transit stop attribute can be specified that contains the 
linkId to (or from) which a route with the given mode should be routed (`linkIdAttribute`).

Additional modes could be configured by adding corresponding parameter sets of type `intermodalAccessEgress`.

Note that when intermodal access and egress is enabled in SwissRailRaptor, `walk` must be
configured as well, as the settings from the default `transitRouter` config group will be
ignored.

If intermodal access and egress legs are created, the default MainModeIdentifier might not 
recognize such trips as pt trips. Therefore, an adapted MainModeIdentifier must be used.
`SwissRailRaptorModule` enables such an adapted one, so it should work out of the box. If you combine
the intermodal SwissRailRaptor with other MATSim extensions, also requiring custom 
MainModeIdentifiers, make sure to provide an implementation combining the different 
requirements correctly.

#### Range Queries

Range queries, sometimes also named profile queries, search for possible connections within a 
time window instead of finding only one connection that arrives with least cost based on a fixed
departure time. As MATSim still requires a single route in the end to be assigned to the agent,
a route must be selected from the returned route set to be assigned to the agent.

To configure SwissRailRaptor to first search for a pt route within a time window, and then 
select a matching route, use the following configuration parameters:

```$xml
<module name="swissRailRaptor">
  <param name="useRangeQuery" value="true" />

  <paramset type="rangeQuerySettings">
    <param name="maxEarlierDeparture_sec" value="600" />
    <param name="maxLaterDeparture_sec" value="900" />
    <param name="subpopulations" value="" /> <!-- an empty value applies to every agent, comma-separated list of multiple subpopulations possible -->
  </paramset>
  <paramset type="routeSelector">
    <param name="betaTravelTime" value="1" />
    <param name="betaDepartureTime" value="1" />
    <param name="betaTransferCount" value="300" />
    <param name="subpopulations" value="" /> <!-- an empty value applies to every agent, comma-separated list of multiple subpopulations possible -->
  </paramset>
</module>
```

The default route selection algorithm (`ch.sbb.matsim.routing.pt.raptor.ConfigurableRaptorRouteSelector`)
supports selecting a route based on a calculated score that depends on the total travel time, the number
of transfers, and the deviation from the desired departure time:

```
score = betaDepartureTime * abs(desiredDepartureTime - effectiveDepartureTime)
        + betaTravelTime * totalTravelTime
        + betaTransfer * transferCount
```

The route with the best (lowest) score will be chosen and returned as a series of legs, to be integrated 
into the agents plan. If multiple routes share the same best score, a random one of this set will be
selected.

Once a route was selected from the calculated choice set, the end time of the previous activity
is adapted to ensure an optimal departure time for the chosen connection. It is possible to 
provide multiple settings for different subpopulations. This allows to have one group of agents 
to be flexible in their departure time choice, while others are not.

***Be aware that range queries infer a large performance penalty!***

Instead of using the built-in, configurable route selection algorithm, a custom implementation
of the interface `ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector` can be provided:

```$java
// somewhere in your main method, where you set up your controler:
controler.addOverridingModule(new AbstractModule() {
    @Override
    public void install() {
        install(new SwissRailRaptorModule());
        bind(RaptorRouteSelector.class).to(MyCustomRouteSelector.class);
    }
});
```
This allows one to implement more complex choice behaviors.

#### Differentiating PT Sub-Modes

By default, the pt router creates legs with mode `pt`. In some cases, it is necessary to
sub-divide the public transport services. This might be the case when some services operate
at special (or strongly different) prices or speeds. For example, slow but luxury class tourist
trains, high-speed trains that require a special ticket, or long distance coaches that are cheaper
but slower than a comparable train service. In all such cases it might be necessary to apply 
different costs to the different services in order to find realistic routes. Also, to stay
consistent, the different costs should be used for the scoring of the executed plans.

SwissRailRaptor supports differentiating pt sub-modes by mapping the transportMode of transit
lines and routes (in the following referred as "route mode") to "passenger modes". The costs for using
such passenger modes can then be configured in the normal `planCalcScore` configuration group.

To configure the passenger mode mappings, add the following section to your `config.xml`:

  ```$xml
  <module name="swissRailRaptor">
    <param name="useModeMappingForPassengers" value="true" />
    
    <paramset type="modeMapping">
      <param name="routeMode" value="train" />
      <param name="passengerMode" value="rail" />
    </paramset>
    <paramset type="modeMapping">
      <param name="routeMode" value="tram" />
      <param name="passengerMode" value="rail" />
    </paramset>
    <paramset type="modeMapping">
      <param name="routeMode" value="bus" />
      <param name="passengerMode" value="road" />
    </paramset>
  </module>
  ```
In the example above, it is assumed that in `transitSchedule.xml` the modes `train`, `tram` 
and `bus` are used as transport modes for the operating services. During route search, the
scoring parameters for the modes `rail` and `road` are used to calculate the costs of using
the respective lines. In the resulting legs that make up the found route, the modes `rail`
and `road` will be used as well instead of the default `pt` that is used by MATSim's default
pt router to describe public transport legs. This implies that next to providing the scoring
parameters for the passenger modes (in the example above `rail` and `road`), these passenger
modes must also be listed as transit modes in the transit configuration, so they will be
correctly recognized and handled as pt passenger legs:

  ```$xml
   <module name="transit">
     <param name="transitModes" value="rail,road" />
   </module>
   ```


#### Person-specific routing-costs

In some scenarios, costs to use public transport may differ from agent to agent. The most
likely application is the combination with pt sub-modes described above: Some agents might
have a season ticket that only applies to certain lines, while other agents don't have such
a season ticket. Or agents might have different values of travel time based on their income,
and thus prefer different services of competing ones.

In order to support such scenarios, SwissRailRaptor provides the interface 
`ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson` which allows to specify the
parameters used for each routing request depending on the agent requesting a route.
By default, a simple implementation `ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson`
is used that returns the same parameters for every request. In order to use a more specialized
implementation, bind your implementation of the `RaptorParametersForPerson` interface as follows:

```$java
// somewhere in your main method, where you set up your controler:
controler.addOverridingModule(new AbstractModule() {
    @Override
    public void install() {
        install(new SwissRailRaptorModule());
        bind(RaptorParametersForPerson.class).to(MyPersonSpecificRaptorParameters.class);
    }
});
```
Make sure to bind your implementation *after* installing the `SwissRailRaptorModule` in order to
actually overwrite the default binding for `RaptorParametersForPerson`.

#### Improved Cost-Calculation for Transfers

The default pt router in MATSim applies a fixed cost term for each transfer during route search.
This can lead to problems, as empirical data shows that perceived costs for transfers depend the
total travel time: A transfer during an urban commute of a total of 15 minutes is perceived 
with a lower disutility than a transfer during a long-distance journey of 2 hours.

SwissRailRaptor supports transfer costs based on the total travel of a route:

  ```$xml
   <module name="swissRailRaptor">
     <param name="transferPenaltyTravelTimeToCostFactor" value="0.0003" />
   </module>
   ```

If the `transferPenaltyTravelTimeToCostFactor` is configured differently from `0.0`,
transfer costs during route search are calculated as:

```
singleTransferCost = fixedTransferCost + totalTravelTime * transferPenaltyTravelTimeToCostFactor;
totalTransferCost = numberOfTransfers * singleTransferCost
```

The fixed transfer cost is taken from the `planCalcScore`'s `utilityOfLineSwitch`, while the 
`transferPenaltyTravelTimetoCostFactor` is taken from the SwissRailRaptor's configuration.

Assuming a travel time disutility of 6 utils per hour, combined with opportunity costs of another
6 utils per hour would result in a total travel time disutility of 0.00333 utils per second
(`(6+6)/3600=0.00333`).
The configured value of 0.0003 in the example above would thus correspond to a single transfer 
having a (non-fixed) cost comparable to 9% of the total travel time.


## Deterministic Public Transport Simulation <span id="detPTSim" />

The deterministic pt simulation is a QSim engine, handling the movement of public transport vehicles
in MATSim. The default `TransitQSimEngine` simulates all pt vehicles on the queue-based network. While
this works well for buses that share the road-infrastructure with private car traffic, it has some drawbacks
when simulating railway transportation. Most notably, trains don't always run at the highest speed
allowed on links (rails) in reality, often resulting in early arrivals when being simulated.

The deterministic pt simulation does not simulate the pt vehicles on the queue network, but uses
its own data structure and "teleports" the vehicles from stop to stop according to the departure 
and arrival times specified in the schedule. Thus, the vehicles operate strictly according to
the transit schedule, hence the name "deterministic" pt simulation.

It is possible to configure the deterministic pt simulation in a way that not all pt vehicles are
simulated deterministically, but that some (e.g. buses) are still simulated on the queue network
and are thus able to interact with private car traffic.

### Usage
To use the deterministic pt simulation, a few things need to be taken into account:

- transportMode of TransitRoutes

  When specifying TransitRoutes in a transit schedule, provide a meaningful `transportMode` to the routes:
  
  ```$xml
  <transitLine id="1">
    <transitRoute id="1">
      <transportMode>train</transportMode>
      <routeProfile>...</routeProfile>
      ...
    </transitRoute>
  </transitLine>
  ```

  The `transportMode` specified in the transit routes is used to determine whether the vehicles serving that
  route should be simulated using the deterministic pt simulation, or on the queue network. By using
  modes like train, bus, metro you can specify which of those should be simulated deterministically (e.g. 
  train and metro), and which should be simulated on the network (e.g. bus).
  
  Do *not* use `pt` as a transportMode in transit routes. This interferes with the mode `pt` that
  passengers use to specify that they want to use a public transport service. The deterministic simulation
  will throw an exception if a transit route with mode `pt` should be simulated deterministically.

- config.xml

  You need an additional config module in your `config.xml`:
  
  ```$xml
  <module name="SBBPt" >
    <param name="deterministicServiceModes" value="train,metro" />
    <param name="createLinkEventsInterval" value="10" />
  </module>
  ```
  The first parameter `deterministicServiceModes` lists all transportModes of transit routes that
  should be simulated deterministically. Multiple modes are separated by a comma in the parameter's value.
  All transportModes of transit routes not specified in this list will be simulated on the queue
  network as usual.
  
  The second parameter `createLinkEventsInterval` specifies in which iteration LinkEnter- and LinkLeave-events should be
  generated for vehicles simulated by the deterministic pt engine. As pt vehicles are teleported 
  between stops by the deterministic pt simulation, they do not create any Link-events by default. 
  But for visualization or analysis purposes it might still be useful to have such events as if the 
  vehicles were actually driving along the links. Set the parameter to `0` to disable the creation
  of link-events. If the parameter is set to a value &gt;0, the 
  deterministic pt simulation will create appropriate Link-events every n-th iteration, similar to
  the controller's `writeEventsInterval`.

Have a look at the class `ch.sbb.matsim.RunSBBExtension` included in the repository to see 
how to enable the deterministic pt simulation when running MATSim. If you already have your own
`QSimModule`, have a look at `ch.sbb.matsim.mobsim.qsim.SBBQSimModule` to see how you can
integrate just the deterministic pt simulation in your own QSim setup.

