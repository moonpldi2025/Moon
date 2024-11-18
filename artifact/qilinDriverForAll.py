import os
import subprocess
from util.benchmark import BENCHMARKS
from moonConfig import unscalable

dir_name = "result"
if not os.path.exists(dir_name):
    os.makedirs(dir_name)
else:
    input(f"{dir_name} exists, press any key to confirm to override results in it.")



def runCommand(command, output_file):
    print(command)
    subprocess.run(command, shell=True, stdout=output_file, stderr=output_file)
    pass
run = 0
for idx, app in enumerate(BENCHMARKS):
    for sens in ["ci", "csc"]:
            print(f"current app: {app}, index: {idx}/{len(BENCHMARKS)}, sens: {sens}")
            command = f"python run.py {sens} {app} -print"
            file_path = f"./{dir_name}/{app}_{sens}.txt"
            with open(file_path, "w") as output_file:
                runCommand(command, output_file)
    for sens in [ "2o", "3o"]: # 
        for algo in ["PLAIN", "MOON", "ZIPPER", "CONCH",  "DEBLOATERX"]: # 
            print(f"current app: {app}, index: {idx}/{len(BENCHMARKS)}, algo: {algo}, sens: {sens}")
            if (app, algo, sens, None) in unscalable:
                continue
            run += 1
            file_path = f"./{dir_name}/{app}_{algo}_{sens}.txt"

            command = ""
            if algo == "PLAIN":
                command = f"python run.py {sens} {app} -print"
            elif algo == "ZIPPER":
                command = f"python run.py Z-{sens} {app} -print"
            else:
                command = f"python run.py {sens} {app} -print -cd -cda={algo}"

            with open(file_path, "w") as output_file:
                runCommand(command, output_file)
