import sys

with open(sys.argv[1], 'r') as fp:
    index = ''
    dest_vertices = []
    for line in fp:
        if line.startswith("#index"):
            if index != '':
                print index + " " + " ".join(dest_vertices)
                dest_vertices = []
            index = line.split("#index")[1].rstrip()
        elif line.startswith("#%"):
            dest_vertices.append(line.split("#%")[1].rstrip())
 
    print index + " " + " ".join(dest_vertices)
