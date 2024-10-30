import argparse
import ast
import glob
import pprint
import re
import matplotlib.pyplot as plt
from hdrh.histogram import HdrHistogram
from hdrh.log import HistogramLogReader
import seaborn as sns
import pandas as pd
import matplotlib as mpl
from matplotlib import pyplot as plt
import numpy as np
pd.set_option('display.max_columns', None)
pd.set_option('display.expand_frame_repr', False)
pd.set_option('max_colwidth', None)
pd.set_option('display.max_rows', None)

plt.rcParams.update({'axes.labelsize': 14, 'axes.titlesize': 16, 'legend.fontsize': 14, 'xtick.labelsize': 14, 'ytick.labelsize': 14})


# mpl.rcParams['text.usetex'] = True
# mpl.rcParams['text.latex.preamble'] = '\\usepackage{libertine}'

MIN_LATENCY_USEC = 1
MAX_LATENCY_USEC = 1000 * 1000 * 1000 # 1 sec
LATENCY_SIGNIFICANT_DIGITS = 5

def append_to_histograms(files, histograms, gc):
  histograms[gc] = []
  for file in files:
    accumulated_histogram = HdrHistogram(MIN_LATENCY_USEC, MAX_LATENCY_USEC, LATENCY_SIGNIFICANT_DIGITS)
    histogram_count = 0
    total_count = 0

    hist = HdrHistogram(MIN_LATENCY_USEC, MAX_LATENCY_USEC, LATENCY_SIGNIFICANT_DIGITS)
    log_reader = HistogramLogReader(file, hist)
    while True:
      decoded_histogram = log_reader.get_next_interval_histogram()
      if not decoded_histogram:
          break
      histogram_count += 1
      accumulated_histogram.add(decoded_histogram)
    histograms[gc].append(accumulated_histogram)
    log_reader.close()

files_multi = {
  "ZGC_20g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/multi/workload_writeintense/serverZGC_20g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr", "/home/jonas/cassandra_package/harbour/log/OpenJDK-21/multi/workload_writeintense/serverZGC_20g_clientG1_32g_target120000.2.hdrIntended-INSERT.hdr"],
  "G1_20g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/multi/workload_writeintense/serverG1_20g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_32g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/multi/workload_writeintense/serverZGC_32g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_32g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/multi/workload_writeintense/serverG1_32g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_64g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/multi/workload_writeintense/serverZGC_64g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_64g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/multi/workload_writeintense/serverG1_64g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"]
}

files_single = {
  "ZGC_20g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/single/workload_writeintense/serverZGC_20g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_20g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/single/workload_writeintense/serverG1_20g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_32g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/single/workload_writeintense/serverZGC_32g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_32g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/single/workload_writeintense/serverG1_32g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_64g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/single/workload_writeintense/serverZGC_64g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_64g": ["/home/jonas/cassandra_package/harbour/log/OpenJDK-21/single/workload_writeintense/serverG1_64g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"]
}


def plot_all(files, file_name):
  histograms = {}
  #["G1_20g", "G1_32g", "G1_64g", "ZGC_20g", "ZGC_32g", "ZGC_64g"]:
  for gc in files.keys():
     #= get_matching_files(get_result_dir(args, gc), args)
    append_to_histograms(files[gc], histograms, gc)
  max_percentile='99.9999'
  # Credits goes to https://github.com/wenyuzhao/lxr-pldi-2022-artifact (license MIT, author Wenyu Zhao)
  # for how to parse HdrHistogram into plot
  percentile_list = []
  for gc, hists in histograms.items():
    for j, histogram in enumerate(hists):
      for i in histogram.get_percentile_iterator(5):
        if i.percentile_level_iterated_to > float(max_percentile):
          continue
        percentile_list.append({"GC": gc, "inv": j, "value": i.value_iterated_to / 1000, "percentile": i.percentile_level_iterated_to})
  percentile_df = pd.DataFrame(percentile_list)
  percentile_df["other"] = 1 / (1 - (percentile_df["percentile"]/100))

  _, ax = plt.subplots(1, 1, figsize=(8, 6))
  sns.color_palette()
  #percentile_df.replace([np.inf], 10000000, inplace=True)
  percentile_df = percentile_df.set_index('GC')
  colorsMap = plt.cm.tab20c.colors

  colors = {
  "G1_20g": colorsMap[0],
  "G1_32g": colorsMap[1],
  "G1_64g": colorsMap[2],
  "ZGC_20g": colorsMap[8],
  "ZGC_32g": colorsMap[9],
  "ZGC_64g": colorsMap[10],
  }

  sns.lineplot(data=percentile_df, x="other", y="value", hue="GC", n_boot=10000, linewidth=2)
  ax.set_ylim(0)
  ax.set_xscale('log')
  ax.set_xlabel('Percentile', labelpad=12)
  ax.set_ylabel('Latency (msec)', labelpad=12)
  labels = ['0', '90', '99', '99.9', '99.99', '99.999', '99.9999']
  ax.set_xticks([1, 10, 100, 1000, 10000, 100000, 1000000]
                  [:labels.index(max_percentile) + 1])
  ax.set_xticklabels(labels[:labels.index(
        max_percentile) + 1])
  handles, labels = ax.get_legend_handles_labels()
  ax.legend(handles=handles[0:], labels=labels[0:], ncols=2, loc="upper left")

  plt.tight_layout()
  plt.savefig(f"{file_name}.pdf", bbox_inches='tight')
  plt.close()

HEAP_SUFFIX = "GMgm"
def validHeapSuffix(str1, str2 = None):
  if str2 is None:
    return str1[-1] in HEAP_SUFFIX
  return str1[-1] in HEAP_SUFFIX and str2[-1] in HEAP_SUFFIX

def argMaybeTuple(heap, mode):
  if mode == 'multi':
    vals = heap.split(',')
    if len(vals) != 4:
      print("In multi mode heap is required to be specified in pairs")
      exit(1)
    if not validHeapSuffix(str(vals[1]), str(vals[3])):
      print("Heap must have a unit suffix, e.g. G or M")
      exit(1)
    return (str(vals[0]), str(vals[1]), str(vals[2]), str(vals[3]))
  elif mode == 'single':
    if not validHeapSuffix(str(heap)):
      print("Heap must have a unit suffix, e.g. G or M")
      exit(1)
    return str(heap)

if __name__ == '__main__':
  parser = argparse.ArgumentParser()
  parser.add_argument('--jdkName', required=True)
  parser.add_argument('--target')
  parser.add_argument('--latencyType',
        choices=['all', 'insert'], default='insert')
  parser.add_argument('--mode',
        choices=['multi', 'single'], required=True)
  parser.add_argument('--workload', required=True)
  ns, args = parser.parse_known_args()
  parser.add_argument('--runs', required=True, type=lambda a:  argMaybeTuple(a, ns.mode), nargs='+',
        help="Multi mode: list (space separator) of tuples (comma separator), e.g. cassandraGC, cassandraHeap,ycsbGC,ycsbHeap")
  parser.add_argument('--dontUseIntended', action='store_true', default=False)
  parser.add_argument('--filterRun', nargs='*')
  args = parser.parse_args()

  target = "" if args.target is None else f"_target{str(args.target)}"
  files = {}
  for run in args.runs:
    files[f"{run[0]}_{run[1]}"] = []
  for run in args.runs:
    path = f"log/{args.jdkName}/{args.mode}/{args.workload}/server{run[0]}_{run[1]}_client{run[2]}_{run[3]}{target}"
    filter = ""
    if args.target is not None and not args.dontUseIntended:
      filter += "Intended-"
    if args.latencyType == 'all':
      filter += "*"
    elif args.latencyType == "insert":
      filter += "INSERT.*"
    if args.filterRun is None:
      for s in glob.glob(f"{path}.*.hdr{filter}*"):
        files[f"{run[0]}_{run[1]}"].append(s)
    else:
      for f in args.filterRun:
        for s in glob.glob(f"{path}.{f}.hdr{filter}*"):
          files[f"{run[0]}_{run[1]}"].append(s)

  pprint.pprint(files)
  plot_all(files, 'testing')