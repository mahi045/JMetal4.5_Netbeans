import pygmo
import numpy as np
import os
import sys
import re

if __name__ == '__main__':
	if len(sys.argv) == 2:
		sol_points_dir = sys.argv[1]
	else:
		print("python3 HV_calc.py <solution point dir>")
		sys.exit(0)	
	
	sol_points_files = [os.path.join(sol_points_dir, filename) for filename in os.listdir(sol_points_dir) if filename.startswith("FUN") ]
	sol_points_obj = []
	solutions = []
	for sol_points_file in sol_points_files:
		obj_file = sol_points_file
		solution_file = os.path.join(os.path.dirname(sol_points_file), "VAR" + os.path.splitext(sol_points_file)[1])
		if not os.path.exists(obj_file ):
			print("{0} not found".format(obj_file))
			sys.exit(0)

		if not os.path.exists(solution_file):
			print("{0} not found".format(solution_file))
			sys.exit(0)	

		with open(obj_file, 'r') as f:
			for sol_point_obj_line in f.readlines():			
				sol_points_obj.append([])
				for idx, sol_point_obj_str in enumerate(sol_point_obj_line.split()):
					if idx != 3:
						sol_points_obj[len(sol_points_obj) - 1].append(float(sol_point_obj_str))

		var_file_route_pattern = r'\[([^\[\]]+)+\]'
		with open(solution_file, 'r') as f:
			for routeset in f.readlines():
				solutions.append([])
				routeset_str = routeset.rstrip()
				route_strs = re.findall(var_file_route_pattern, routeset_str)
				for route_str in route_strs:
					solutions[-1].append([])
					route_str = route_str.split(", ")
					for node_str in route_str:
						solutions[-1][-1].append(int(node_str)) 
				#	sol_points_obj[len(sol_points) - 1].append(float(sol_point_obj_str))
	# non dominated sorting
	ndf, dl, dc, ndr = pygmo.fast_non_dominated_sorting(points = sol_points_obj)
	
	# min-max normalize
	sol_points_obj_np = np.array(sol_points_obj)
	sol_points_obj_ndf = sol_points_obj_np[ndf[0]]
	#solutions_ndf = np.array(solutions)[ndf[0]]
	#for sol_point_obj in sol_points_obj_ndf:
		#for obj in sol_point_obj:
			#print(obj, end=' ')
		#print('')

	sol_points_obj_np_norm = (sol_points_obj_np - sol_points_obj_ndf.min(axis=0)) / (sol_points_obj_ndf.max(axis=0) - sol_points_obj_ndf.min(axis=0)) 

	
	# hypervolume object
	# hv_object = pygmo.hypervolume(points = sol_points_obj_np_norm[ndf[0]])
	
	# ref point
	# nadir_point = pygmo.nadir(points = sol_points_obj_np_norm)


	# hv_val = hv_object.compute(nadir_point)
	# for min hop count and max hop count statistics
	min_hop_count = float("inf")
	max_hop_count = 0
	for idx in ndf[0]:
		hop_count = 0
		for route in solutions[idx]:
			print(route, end='')
			hop_count = len(route)

			# to calculte min max hop count stat
			if hop_count > max_hop_count:
				max_hop_count = hop_count
			if hop_count < min_hop_count:
				min_hop_count = hop_count
		print('')

	print("\n\n")
	print("NDF solution max hop count : {0}".format(max_hop_count))
	print("NDF solution min hop count : {0}".format(min_hop_count))

