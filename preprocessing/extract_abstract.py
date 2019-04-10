import sys

with open(sys.argv[1], 'r') as fp:
    index = ''
    abstract_start = False
    for line in fp:
        if line.startswith("#!"):
            abstract_start = True
            abstract = line.split("#!")[1].rstrip() + " "
        elif abstract_start:
            if line.startswith("#"):
                abstract_start = False
                print index + "###" + abstract
            else:
                abstract += line.rstrip() + " "
        elif line.startswith("#index"):
            index = line.split("#index")[1].rstrip()
    
    if abstract_start:
        print index + "###" + abstract
