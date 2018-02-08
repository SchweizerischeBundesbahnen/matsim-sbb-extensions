# MATSim-Extensions by SBB

The following extensions for [MATSim](http://www.matsim.org/) are provided by
the [Swiss Federal Railways](http://www.sbb.ch/) (SBB, Schweizerische Bundesbahnen).

[![](https://jitpack.io/v/SchweizerischeBundesbahnen/matsim-sbb-extensions.svg)](https://jitpack.io/#SchweizerischeBundesbahnen/matsim-sbb-extensions)

 **Step 1**. Add the JitPack repository to you `pom.xml`.
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

## SwissRailRaptor

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

SwissRailRaptor acts as a drop-in replacement for the pt router included in MATSim by default.
It does not require additional configuration, but re-uses the configuration parameters from the
default `transitRouter` config group.

A major difference to the default transit router in MATSim is the fact that SwissRailRaptor 
does not repeat the transit schedule after 24 hours when searching for a route,
but only takes the actual departure times as specified in the schedule into account. This is due
to the fact that not all schedules have a periodicity of 24 hours. When applied in MATSim, it can result
in agents no longer finding a route when departing late at night. Considering such agents would have
gotten stuck in the simulation anyway due to no scheduled pt vehicles running at that time 
the next day, this should not pose any real problem.

Have a look at the class `ch.sbb.matsim.RunSBBExtension` included in the repository to see 
how to enable SwissRailRaptor when running MATSim.


## Deterministic Public Transport Simulation

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
    <param name="createLinkEvents" value="true" />
  </module>
  ```
  The first parameter `deterministicServiceModes` lists all transportModes of transit routes that
  should be simulated deterministically. Multiple modes are separated by a comma in the parameter's value.
  All transportModes of transit routes not specified in this list will be simulated on the queue
  network as usual.
  
  The second parameter `createLinkEvents` specifies if LinkEnter- and LinkLeave-events should be
  generated for vehicles simulated by the deterministic pt engine. As pt vehicles are teleported 
  between stops by the deterministic pt simulation, they do not create any Link-events by default. 
  But for visualization or analysis purposes it might still be useful to have such events as if the 
  vehicles were actually driving along the links. Set the parameter to `true` to make the 
  deterministic pt simulation create appropriate Link-events.

Have a look at the class `ch.sbb.matsim.RunSBBExtension` included in the repository to see 
how to enable the deterministic pt simulation when running MATSim. If you already have your own
`QSimModule`, have a look at `ch.sbb.matsim.mobsim.qsim.SBBQSimModule` to see how you can
integrate just the deterministic pt simulation in your own QSim setup.

