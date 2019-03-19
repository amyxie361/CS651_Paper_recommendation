ids = {}
paper_id = 1
index = ''

def add_to_id_mapping(index):
    global ids
    global paper_id

    if index not in ids:
        ids[index] = paper_id
        paper_id += 1


with open('./citation-acm-v8.txt', 'r') as fp:
    abstract_start = False
    for line in fp:
        if line.startswith("#@") or line.startswith("#t") or line.startswith("#c"):
            continue

        if line.startswith("#index"):
            index = line.split("#index")[1].rstrip()
            add_to_id_mapping(index)
            print "#index" + str(ids[index])
        elif line.startswith("#%"):
            index = line.split("#%")[1].rstrip()
            add_to_id_mapping(index)
            print "#%" + str(ids[index])
        else:
            print line.rstrip()

print "*********"

for key in ids:
    print key + "," + str(ids[key])
