import simpy, argparse
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
        if configuration.DISPLAY:
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
        if configuration.FLEET_DISPLAY:
            print('%s starts at %.2f.' % (fleet_number, simulator.env.now))
        vacancies = capacity
        des_shelter_index = configuration.get_shelter_index(route[-1])
        start_index = 1
        last_req = None
        last_resource = None
        new_req = None
        trip_number = 1
        while True:
            start_index = max(1, start_index)
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
                        if configuration.DISPLAY:
                            print('%s stops at bus stop (%d) at %.2f.' % (fleet_number, node1, simulator.env.now))
                        evacuee = min(vacancies, simulator.all_bus_stop[node1].number_of_evacuee[des_shelter_index])
                        simulator.all_bus_stop[node1].number_of_evacuee[des_shelter_index] -= evacuee
                        vacancies -= evacuee
                        last_req = new_req
                        last_resource = simulator.all_bus_stop[node1].machine

                if configuration.DISPLAY:
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

            if configuration.DISPLAY:
                print('Trip %d of %s goes to shelter at %.2f.' % (trip_number, fleet_number, simulator.env.now))
            time_stamp = (int) (simulator.env.now // configuration.STAT_INTERVAL)
            if time_stamp not in configuration.STAT_EVACUEE:
                configuration.STAT_EVACUEE[time_stamp] = (capacity - vacancies)
            else:
                configuration.STAT_EVACUEE[time_stamp] += (capacity - vacancies)
            # capacity will be zero
            vacancies = capacity
            request_time = simulator.env.now
            new_req = simulator.all_bus_stop[route[-1]].machine.request()
            if configuration.DISPLAY:
                print('%s request bus stop (%d) at %.2f.' % (fleet_number, route[-1], simulator.env.now))
            yield new_req
            configuration.WAITING_TIME += (simulator.env.now - request_time)
            if last_req != None or last_resource != None:
                last_resource.release(last_req)
            # yield give times to get off the bus
            yield simulator.env.timeout(configuration.SHELTER_EVACUATION_TIME)
            last_req = new_req
            last_resource = simulator.all_bus_stop[route[-1]].machine
            # if trip_number == trip:
            #     if last_req != None or last_resource != None:
            #         last_resource.release(last_req)
            #     time_stamp = (int) (simulator.env.now // configuration.STAT_INTERVAL)
            #     if time_stamp not in configuration.STAT_FLEET:
            #         configuration.STAT_FLEET[time_stamp] = 1
            #     else:
            #         configuration.STAT_FLEET[time_stamp] += 1
            #     print('%s done all the trips at %.2f.' % (fleet_number, simulator.env.now))
            #     # no backward journey required
            #     break

            time_stamp = (int) (simulator.env.now // configuration.STAT_INTERVAL)
            if time_stamp not in configuration.STAT_FLEET:
                configuration.STAT_FLEET[time_stamp] = 1
            else:
                configuration.STAT_FLEET[time_stamp] += 1

            start_index = -1
            for _ in range(0, size):
                node1 = route[_]

                if simulator.all_bus_stop[node1].number_of_evacuee[des_shelter_index] > 0:
                    # node1 has some evacuees
                    start_index = _

            if start_index == -1:
                # no trip needed
                if last_req != None or last_resource != None:
                    last_resource.release(last_req)

                configuration.FLEET_DONE += 1
                if configuration.FLEET_DISPLAY:
                    print('%s done all the trips at %.2f.' % (fleet_number, simulator.env.now))
                if configuration.FLEET_DONE == configuration.NUMBER_OF_FLEET:
                    print('Total Evacuation Time: {0}'.format(simulator.env.now))
                break

            for _ in range(size - 1, start_index, -1):
                node1 = route[_]
                node2 = route[_ - 1]

                if configuration.DISPLAY:
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
                # if simulator.all_bus_stop[node2].number_of_evacuee[des_shelter_index] == 0:
                #     start_index = _
                #     break

            if configuration.DISPLAY:
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
                        self, fleet_name, configuration.BUS_CAPACITY, route_list, delay, 
                        trip_count, stop_list=route_stop_list
                    )
                )
                fleet_name = f.readline().strip()
                route_no += 1
        to_shelter1 = 0
        to_shelter2 = 0
        # bus_stop.txt contains description of all bus stops
        with open(stopdata_filepath, 'r') as f:
            for line in f:
                l = [int(_) for _ in line.split()]
                to_shelter1 += l[1]
                to_shelter2 += l[2]
                self.all_bus_stop[l[0]] = Bus_Stop(self.env, l[0], l[1], l[2])

        # print(to_shelter1, to_shelter2)

        # bus_stop.txt contains description of all bus stops
        with open(routedata_filepath, 'r') as f:
            for line in f:
                l = line.split()
                l[0] = int(l[0])
                l[1] = int(l[1])
                l[2] = float(l[2])
                route_capacity = max(l[2] // configuration.BUS_LENGTH + 1, 1)
                route_passing_time = l[2] / configuration.BUS_SPEED
                l[3] = int(l[3])
                assert(l[3] == 1 or l[3] == 2)
                if l[3] == 2:
                    self.all_edge[l[0], l[1]] = Edge(self.env, route_passing_time, route_capacity * configuration.NUMBER_OF_LANE, l[0], l[1])
                    self.all_edge[l[1], l[0]] = Edge(self.env, route_passing_time, route_capacity *  configuration.NUMBER_OF_LANE, l[1], l[0])
                else:
                    self.all_edge[l[0], l[1]] = Edge(self.env, route_passing_time, route_capacity * configuration.NUMBER_OF_LANE/2, l[0], l[1])
                    self.all_edge[l[1], l[0]] = Edge(self.env, route_passing_time, route_capacity * configuration.NUMBER_OF_LANE/2, l[1], l[0])

    def simulate(self):
        # Execute!
        self.env.run(until=configuration.SIM_TIME)
        print("Waiting time: {0}".format(configuration.WAITING_TIME))
        # Here is all the statictics 
        # print(configuration.STAT_FLEET)
        # print(configuration.STAT_EVACUEE)
        a = 0
        for _ in configuration.STAT_EVACUEE:
            print('At {0}th hour #evacuees: {1}'.format(_, configuration.STAT_EVACUEE[_]))
            a += configuration.STAT_EVACUEE[_]

        print('Number of evacuaees: {0}'.format(a))
        a = 0
        for _ in configuration.STAT_FLEET:
            print('At {0}th hour #trips: {1}'.format(_, configuration.STAT_FLEET[_]))
            a += configuration.STAT_FLEET[_]

        print('Number of trips: {0}'.format(a))



if __name__ == "__main__":
    # edit file path here to change data source
    parser = argparse.ArgumentParser()
    parser.add_argument('-i','--i', help='Description', required=True)
    args = parser.parse_args()
    bus_stop_filepath = "examples/Bus_Stops{0}.txt".format(args.i)
    bus_route_filepath = "examples/Bus_Edge{0}.txt".format(args.i)
    bus_fleet_filepath = "examples/Bus_Fleet{0}.txt".format(args.i)

    simulator: Simulator = Simulator()
    # provide datafile and prepare internal datastructure and environment
    simulator.parse_data_prep_env(
        routedata_filepath = bus_route_filepath, 
        fleetdata_filepath = bus_fleet_filepath, 
        stopdata_filepath = bus_stop_filepath
    )
    # simulate
    simulator.simulate()

