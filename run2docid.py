f = open("run.mb13.ql.topics.microblog2013.txt")
f2 = open("run.mb13.ql.topics.microblog2013.docids.txt", "w")

for l in f:
    ls = l.split()
    docid = ls[2]
    f2.write("{}\n".format(docid))

f.close()
f2.close()
