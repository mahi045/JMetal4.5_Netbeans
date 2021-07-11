import matplotlib.pyplot as plotter
import os
import re
import sys


def parse_data(result_dir: str):
    # file parse related regex
    REGEX_WAITTIME = r"^Waiting time: (\d*\.\d+)"
    REGEX_EVACTIME = r"^Total Evacuation Time: (\d*\.\d+)"
    REGEX_TRIPCOUNT = r"^Number of trips: (\d+)"

    # data structure to contain parsed metric from file
    list_waiting_time = []
    list_trip_count = []
    list_evacuation_time = []

    total_parsed_file = 0
    for filename in os.listdir(result_dir):
        # file should of type text and with prefix "result"
        basename = os.path.splitext(filename)[0]
        extension = os.path.splitext(filename)[1]

        if extension != ".txt" or not basename.startswith("result"):
            continue
        
        filepath = os.path.join(result_dir, filename)
        with open(filepath) as fin:
            for logline in fin.readlines():
                result = re.search(REGEX_EVACTIME, logline)
                if result is not None:
                    list_evacuation_time.append(float(result.groups()[0]))
                    continue
                result = re.search(REGEX_WAITTIME, logline)
                if result is not None:
                    list_waiting_time.append(float(result.groups()[0]))
                    continue
                result = re.search(REGEX_TRIPCOUNT, logline)
                if result is not None:
                    list_trip_count.append(int(result.groups()[0]))
                    continue
        total_parsed_file += 1

    # check sanity of data parsing
    # if parsing is correct parsed file count should be equal to data point count for each metrics
    assert len(list_evacuation_time) == len(list_trip_count) == len(list_waiting_time) == total_parsed_file,\
        """
        mismatch in data point count and parsed file count 
        total file : {0} 
        tripcount data point count : {1} 
        evactime data point count : {2} 
        waittime data point count : {3}
        """.format(total_parsed_file, len(list_trip_count), len(list_evacuation_time), len(list_waiting_time))

    return list_evacuation_time, list_waiting_time, list_trip_count


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("python3 boxplot_generate.py <folder path(s) containing results file>")
        exit(0)

    # data structure to contain parsed metric from file
    list_waiting_time = []
    list_trip_count = []
    list_evacuation_time = []

    for dirname in sys.argv[1:]:
        ret_list0, ret_list1, ret_list2 = parse_data(dirname)
        list_evacuation_time.append(ret_list0)
        list_waiting_time.append(ret_list1)
        list_trip_count.append(ret_list2)

    # generate plot
    xticks_name = [dirname.split('/')[-1] for dirname in sys.argv[1:]]
    # for outlier indication
    red_square = dict(markerfacecolor='r', marker='s')

    # generate evacuation time plot
    fig, ax = plotter.subplots()
    #fig.set_title(result_dir)
    ax.set_title("evacuation time")
    ax.boxplot(list_evacuation_time, showfliers=False, flierprops=red_square)
    plotter.xticks(list(range(1, len(sys.argv[1:]) + 1)), xticks_name)

    # plotter.show()
    plotter.savefig("evacuation_time.png", dpi=300)
    
    fig, ax = plotter.subplots()
    #fig.set_title(result_dir)
    ax.set_title("evacuation time with outliers")
    ax.boxplot(list_evacuation_time, flierprops=red_square)
    plotter.xticks(list(range(1, len(sys.argv[1:]) + 1)), xticks_name)

    # plotter.show()
    plotter.savefig("evacuation_time_with_outliers.png", dpi=300)
    
    # generate wait time plot
    fig, ax = plotter.subplots()
    #fig.set_title(result_dir)
    ax.set_title("waiting time")
    ax.boxplot(list_waiting_time, showfliers=False, flierprops=red_square)
    plotter.xticks(list(range(1, len(sys.argv[1:]) + 1)), xticks_name)

    # plotter.show()
    plotter.savefig("waiting_time.png", dpi=300)

    fig, ax = plotter.subplots()
    #fig.set_title(result_dir)
    ax.set_title("waiting time with outliers")
    ax.boxplot(list_waiting_time, flierprops=red_square)
    plotter.xticks(list(range(1, len(sys.argv[1:]) + 1)), xticks_name)

    # plotter.show()
    plotter.savefig("waiting_time_with_outliers.png", dpi=300)

    # generate trip count plot
    fig, ax = plotter.subplots()
    #fig.set_title(result_dir)
    ax.set_title("trip count")
    ax.boxplot(list_trip_count, showfliers=False, flierprops=red_square)
    plotter.xticks(list(range(1, len(sys.argv[1:]) + 1)), xticks_name)

    # plotter.show()
    plotter.savefig("trip_count.png", dpi=300)   

    fig, ax = plotter.subplots()
    #fig.set_title(result_dir)
    ax.set_title("trip count with outliers")
    ax.boxplot(list_trip_count, flierprops=red_square)
    plotter.xticks(list(range(1, len(sys.argv[1:]) + 1)), xticks_name)

    # plotter.show()
    plotter.savefig("trip_count_with_outliers.png", dpi=300)
