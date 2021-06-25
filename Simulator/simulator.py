import simpy
import configuration

class Edge(object):
    """A carwash has a limited number of machines (``NUM_MACHINES``) to
    clean cars in parallel.

    Cars have to request one of the machines. When they got one, they
    can start the washing processes and wait for it to finish (which
    takes ``washtime`` minutes).

    """
    def __init__(self, env, passing_time, capacity, from_node, to_node):
        self.env = env
        self.machine = simpy.Resource(env, capacity)
        self.passing_time = passing_time
        self.from_node = from_node
        self.to_node = to_node

    def pass_the_edge(self, fleet_name):
        """The washing processes. It takes a ``car`` processes and tries
        to clean it."""
        yield self.env.timeout(self.passing_time)
        print("%s passed edge(%d, %d) at %.2f." %
              (fleet_name, self.from_node, self.to_node, self.env.now))

class Bus_Stop(object):
    """A carwash has a limited number of machines (``NUM_MACHINES``) to
    clean cars in parallel.

    Cars have to request one of the machines. When they got one, they
    can start the washing processes and wait for it to finish (which
    takes ``washtime`` minutes).

    """
    def __init__(self, env, number, evacuee1, evacuee2):
        self.env = env
        self.number = number
        self.machine = simpy.Resource(env, configuration.NUMBER_OF_LANE)
        self.standing_time = configuration.BUS_STOP_STANDING_TIME
        self.number_of_evacuee = [evacuee1, evacuee2]

# forward declaration for route process
class Simulator:
    pass

def route_process(
    simulator: Simulator, fleet_number: str, capacity:int, route: list, 
    interval: float, trip: int, stop_list: list=None):
        """The car process (each car has a ``name``) arrives at the carwash
        (``cw``) and requests a cleaning machine.

        It then starts the washing process, waits for it to finish and
        leaves to never come back ...

        """
        yield simulator.env.timeout(interval)
        size = len(route)
        print('%s starts at %.2f.' % (fleet_number, simulator.env.now))
        vacancies = capacity
        des_shelter_index = configuration.get_shelter_index(route[-1])
        start_index = 1
        last_req = None
        last_resource = None
        new_req = None
        for trip_number in range(1, trip + 1):
            # start_index = 1
            for _ in range(start_index, size):
                node1 = route[_ - 1]
                node2 = route[_]

                # first check if node1 in stop list
                if simulator.stop_list is None or node1 in simulator.stop_list:
                    if simulator.all_bus_stop[node1].number_of_evacuee[des_shelter_index] > 0 and vacancies > 0:
                        request_time = simulator.env.now
                        new_req = simulator.all_bus_stop[node1].machine.request()
                        yield new_req
                        configuration.WAITING_TIME += (simulator.env.now - request_time)
                        if last_req != None or last_resource != None:
                            last_resource.release(last_req)
                        yield simulator.env.timeout(configuration.BUS_STOP_STANDING_TIME)  
                        print('%s stops at bus stop (%d) at %.2f.' % (fleet_number, node1, simulator.env.now))
                        evacuee = min(vacancies, simulator.all_bus_stop[node1].number_of_evacuee[des_shelter_index])
                        simulator.all_bus_stop[node1].number_of_evacuee[des_shelter_index] -= evacuee
                        vacancies -= evacuee
                        last_req = new_req
                        last_resource = simulator.all_bus_stop[node1].machine

                print('%s request (%d, %d) at %.2f.' % (fleet_number, node1, node2, simulator.env.now))
                request_time = simulator.env.now
                new_req = simulator.all_edge[node1,node2].machine.request()
                yield new_req
                configuration.WAITING_TIME += (simulator.env.now - request_time)
                if last_req != None or last_resource != None:
                    last_resource.release(last_req)
                yield simulator.env.process(simulator.all_edge[node1,node2].pass_the_edge(fleet_number))
                last_req = new_req
                last_resource = simulator.all_edge[node1,node2].machine

            print('Trip %d of %s goes to shelter at %.2f.' % (trip_number, fleet_number, simulator.env.now))
            # capacity will be zero
            vacancies = capacity
            request_time = simulator.env.now
            new_req = simulator.all_bus_stop[route[-1]].machine.request()
            print('%s request bus stop (%d) at %.2f.' % (fleet_number, route[-1], simulator.env.now))
            yield new_req
            configuration.WAITING_TIME += (simulator.env.now - request_time)
            if last_req != None or last_resource != None:
                last_resource.release(last_req)
            # yield give times to get off the bus
            yield simulator.env.timeout(configuration.SHELTER_EVACUATION_TIME)
            last_req = new_req
            last_resource = simulator.all_bus_stop[route[-1]].machine
            if trip_number == trip:
                if last_req != None or last_resource != None:
                    last_resource.release(last_req)
                print('%s done all the trips at %.2f.' % (fleet_number, simulator.env.now))
                # no backward journey required
                break

            for _ in range(size - 1, 0, -1):
                node1 = route[_]
                node2 = route[_ - 1]

                print('%s request (%d, %d) at %.2f.' % (fleet_number, node1, node2, simulator.env.now))
                request_time = simulator.env.now
                new_req = simulator.all_edge[node1,node2].machine.request()
                yield new_req
                configuration.WAITING_TIME += (simulator.env.now - request_time)
                if last_req != None or last_resource != None:
                    last_resource.release(last_req)
                yield simulator.env.process(simulator.all_edge[node1,node2].pass_the_edge(fleet_number))
                last_req = new_req
                last_resource = simulator.all_edge[node1,node2].machine
                if simulator.all_bus_stop[node2].number_of_evacuee[des_shelter_index] == 0:
                    start_index = _
                    break

            print('Trip %d of %s goes to start node %d at %.2f.' % (trip_number, fleet_number, start_index, simulator.env.now))


class Simulator:
    def __init__(self):
        # type hint given
        self.all_edge: dict = dict()
        self.all_bus_stop: dict = dict()
        self.stop_list: list = None

        self.env: simpy.core.Environment  = simpy.Environment()

    def parse_data_prep_env(self, routedata_filepath, fleetdata_filepath, \
                            stopdata_filepath, perroutestopdata_filepath=None):
        if perroutestopdata_filepath is not None:
            # route stop list data is given
            self.stop_list = []
             # per route stop nodes
            with open(perroutestopdata_filepath, 'r') as f:
                for line in f:
                    l = [int(_) for _ in line.split()]
                    self.stop_list.append(l)

        # fleet.txt contains description of all fleets
        with open(fleetdata_filepath, 'r') as f: 
            fleet_name = f.readline().strip()
            # to keep count of route and accordingly provide route stop list
            route_no = 0
            route_list = []
            while fleet_name:
                route_des = f.readline()
                route_list = [int(_) for _ in route_des.split()]
                delay =  float(f.readline())
                trip_count = int(f.readline())
                
                # get route specific stop list
                if self.stop_list is None:
                    route_stop_list = None
                else:
                    route_stop_list = self.stop_list[route_no]
                
                self.env.process(
                    route_process(
                        self, fleet_name, 50, route_list, delay, 
                        trip_count, stop_list=route_stop_list
                    )
                )
                fleet_name = f.readline().strip()
                route_no += 1

        # bus_stop.txt contains description of all bus stops
        with open(stopdata_filepath, 'r') as f:
            for line in f:
                l = [int(_) for _ in line.split()]
                self.all_bus_stop[l[0]] = Bus_Stop(self.env, l[0], l[1], l[2])

        # bus_stop.txt contains description of all bus stops
        with open(routedata_filepath, 'r') as f:
            for line in f:
                l = [int(_) for _ in line.split()]
                l[2] = l[2] * 1 #hypeparameter
                assert(l[3] == 1 or l[3] == 2)
                if l[3] == 2:
                    self.all_edge[l[0], l[1]] = Edge(self.env, l[2], configuration.NUMBER_OF_LANE, l[0], l[1])
                    self.all_edge[l[1], l[0]] = Edge(self.env, l[2], configuration.NUMBER_OF_LANE, l[1], l[0])
                else:
                    self.all_edge[l[0], l[1]] = Edge(self.env, l[2], configuration.NUMBER_OF_LANE/2, l[0], l[1])
                    self.all_edge[l[1], l[0]] = Edge(self.env, l[2], configuration.NUMBER_OF_LANE/2, l[1], l[0])

    def simulate(self):
        # Execute!
        self.env.run(until=configuration.SIM_TIME)
        print("Waiting time: {0}".format(configuration.WAITING_TIME))


if __name__ == "__main__":
    # edit file path here to change data source
    bus_stop_filepath = "bus_stop.txt"
    bus_route_filepath = "bus_route.txt"
    bus_fleet_filepath = "bus_fleet.txt"

    simulator: Simulator = Simulator()
    # provide datafile and prepare internal datastructure and environment
    simulator.parse_data_prep_env(
        routedata_filepath = bus_route_filepath, 
        fleetdata_filepath = bus_fleet_filepath, 
        stopdata_filepath = bus_stop_filepath
    )
    # simulate
    simulator.simulate()

