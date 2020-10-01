import os
import json 

namespace = 'fps.general.step'
name = 'random'

ls = os.listdir()

sounds = [ ]

for f in ls:
  if not f.endswith('.ogg'):
    continue
  x = '%s.%s' % (namespace, f[:-4])
  sounds.append({
    'type': 'event',
    'name': x
  })
  print(x)

data = {
  '%s.%s' % (namespace, name): {
    'sounds': sounds
  }
}

print(data)

with open('%s.%s.merge.json' % (namespace, name), 'w') as out:
  out.write(json.dumps(data))

print('Random sound <%s> has been generated! ' % ('%s.%s.merge.json' % (namespace, name)))
