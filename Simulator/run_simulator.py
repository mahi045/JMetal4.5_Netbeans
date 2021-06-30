import os

for n in range(1, 10):
    os.system('python3 simulator.py -i {0} > output/result{1}.txt'.format(n, n))