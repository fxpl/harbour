import matplotlib.pyplot as plt
from hdrh.histogram import HdrHistogram
from hdrh.log import HistogramLogReader
import seaborn as sns
import pandas
from matplotlib import pyplot as plt
from typing import *
import numpy as np


MIN_LATENCY_USEC = 1
MAX_LATENCY_USEC = 1000 * 1000 # 1 sec
LATENCY_SIGNIFICANT_DIGITS = 5

def append_to_histograms(files, histograms, gc):
  histograms[gc] = []
  for file in files:
    print(file)
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
      total_count += decoded_histogram.get_total_count()
      accumulated_histogram.add(decoded_histogram)
    print(total_count)
    histograms[gc].append(accumulated_histogram)
    log_reader.close()

files_multi = {
  "ZGC_16g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/multi/workload_writeintense/serverZGC_16g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_16g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/multi/workload_writeintense/serverG1_16g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_32g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/multi/workload_writeintense/serverZGC_32g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_32g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/multi/workload_writeintense/serverG1_32g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_64g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/multi/workload_writeintense/serverZGC_64g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_64g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/multi/workload_writeintense/serverG1_64g_clientG1_32g_target120000.1.hdrIntended-INSERT.hdr"]
}

files_single = {
  "ZGC_16g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/single/workload_writeintense/serverZGC_16g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_16g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/single/workload_writeintense/serverG1_16g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_32g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/single/workload_writeintense/serverZGC_32g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_32g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/single/workload_writeintense/serverG1_32g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],

  "ZGC_64g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/single/workload_writeintense/serverZGC_64g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"],
  "G1_64g": ["/home/jonas/cassandra_package/harbour/dist/harbour-0.0.2/log/OpenJDK-21/single/workload_writeintense/serverG1_64g_clientnull_null_target120000.1.hdrIntended-INSERT.hdr"]
}

colorsMap = plt.cm.tab20c.colors

colors = {
"G1_16g": colorsMap[0],
"G1_32g": colorsMap[1],
"G1_64g": colorsMap[2],
"ZGC_16g": colorsMap[4],
"ZGC_32g": colorsMap[5],
"ZGC_64g": colorsMap[6],
}

import sys
pandas.set_option('display.max_columns', None)
pandas.set_option('display.expand_frame_repr', False)
pandas.set_option('max_colwidth', -1)
pandas.set_option('display.max_rows', None)
def plot_all():
  files = files_multi
  histograms = {}
  for gc in ["G1_16g", "G1_32g", "G1_64g", "ZGC_16g", "ZGC_32g", "ZGC_64g"]:
     #= get_matching_files(get_result_dir(args, gc), args)
    append_to_histograms(files[gc], histograms, gc)
  max_percentile='99.9999'

  percentile_list = []
  for gc, hists in histograms.items():
    for j, histogram in enumerate(hists):
      for i in histogram.get_percentile_iterator(5):
        if i.percentile_level_iterated_to > float(max_percentile):
          continue
        percentile_list.append({"GC": gc, "inv": j, "value": i.value_iterated_to / 1000, "percentile": i.percentile_level_iterated_to})
  percentile_df = pandas.DataFrame(percentile_list)
  percentile_df["other"] = 1 / (1 - (percentile_df["percentile"]/100))

  _, ax = plt.subplots(1, 1, figsize=(8, 6))
  sns.color_palette()
  #percentile_df.replace([np.inf], 10000000, inplace=True)
  percentile_df = percentile_df.set_index('GC')
  print(percentile_df)

  sns.lineplot(data=percentile_df, x="other", y="value", hue="GC", n_boot=1000, palette=colors)
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
  plt.savefig(f"multi.pdf", bbox_inches='tight')
  plt.close()

plot_all()