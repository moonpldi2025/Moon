import os
import subprocess
from util.benchmark import BENCHMARKS

dir_name = "result-abletion"
if not os.path.exists(dir_name):
    os.makedirs(dir_name)
else:
    input(f"{dir_name} exists, press any key to confirm to override results in it.")





run = 0
for idx, app in enumerate(BENCHMARKS):
    for algo in ["MOONu", "MOONuf", "MOONuh"]:
        for sens in ["2o", "3o"]: # , 
            if sens == "2o" and algo != "MOONu":
                continue
            print(f"No.{run}: current app: {app}, index: {idx}/{len(BENCHMARKS)}, algo: {algo}, sens: {sens}")
            run += 1
            file_path = f"./{dir_name}/{app}_{algo}_{sens}.txt"

            command = f"python run.py {sens} {app} -print -cd -cda={algo}"

            with open(file_path, "w") as output_file:
                subprocess.run(
                    command, shell=True, stdout=output_file, stderr=output_file
                )
