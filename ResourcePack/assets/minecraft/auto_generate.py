#
# 自动生成 sounds.json 音频文件
# 作者: 城主Kevin @ bilibili
# 

import json
import os
from os.path import join, getsize

data = {}

rootDir = 'sounds'
for dirName, subdirList, fileList in os.walk(rootDir):
    for fname in fileList:
        full_name = join(dirName, fname)
        if not fname.startswith('amb.'):
          data_path = full_name[len(rootDir)+1:-4].replace('\\', '.')
          amb = False
        else:
          x = join(dirName, fname[4:])
          data_path = x[len(rootDir)+1:-4].replace('\\', '.')
          amb = True
        data_file_path = full_name[len(rootDir)+1:-4].replace('\\', '/')
        single_data = []
        node = {
          "name": data_file_path,
        }
        if getsize(full_name)>100*1024:
          node['stream'] = True
          node['preload'] = True
        if amb:
          node['attenuation_distance'] = 0.0
        single_data.append(node)
        data[data_path] = {
          "subtitle": data_path,
          "sounds": single_data
        }


merge_jsons = os.listdir()
for mf in merge_jsons:
  if not mf.endswith('.merge.json'):
    continue
  print('merging ', mf)
  with open(mf, "r") as fp:
    template = json.loads(fp.read())
  data = {**data, **template}

with open("sounds.json", "w") as fp:
  fp.write(json.dumps(data))

print('complete! ')
