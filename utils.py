import json


def get_qid2query(ftopic):
    qid2query = {}
    f = open(ftopic)
    query_tag = "title"
    empty = False
    for l in f:
        if empty == True:
            qid2query[qid] = l.replace("\n", "").strip()
            empty = False
        ind = l.find("Number: ")
        if ind >= 0:
            qid = l[ind+8:-1]
            qid = int(qid) 
        ind = l.find("<{}>".format(query_tag))
        if ind >= 0:
            query = l[ind+8:-1].strip()
            if len(query) == 0:
                empty = True
            else:
                qid2query[qid] = query
    return qid2query

def get_qid2query_clueweb(ftopic):
    qid2query = {}
    f = open(ftopic)
    query_tag = "title"
    empty = False
    for l in f:
        if empty == True:
            qid2query[qid] = l.replace("\n", "").strip()
            empty = False
        ind = l.find("Number: ")
        if ind >= 0:
            qid = l[ind+8:-1]
            qid = int(qid) 
        ind = l.find("<{}>".format(query_tag))
        if ind >= 0:
            query = l[ind+8:-1].strip()
            if len(query) == 0:
                empty = True
            else:
                qid2query[qid] = query
    return qid2query

def getdoc(docid, folder):
    f = open("{}/{}".format(folder, docid))
    see_text = False
    doc = ""
    for l in f:
        l = l.replace("\n", "").strip()
        if l == "</TEXT>":
            break
        if see_text:
            if len(l) > 0:
                doc += l + " "
        elif "[Text]" in l:
            see_text = True
            doc += l[len("[Text]") + 1:] + " "
    return doc.strip()

def get_qid2reldocids(fqrel):
    f = open(fqrel)
    qid2reldocids = {}
    for l in f:
        qid, _, docid, score = l.replace("\n", "").strip().split()
        qid = int(qid)
        if score != "0" or score != "-2":
            if qid not in qid2reldocids:
                qid2reldocids[qid] = set()
            qid2reldocids[qid].add(docid)
    return qid2reldocids

def get_doc_from_index(indexes, docid):
    doc_raw = indexes.getRawDocument(JString(docid))
    doc = json.loads(doc_raw)['text']
    return doc

def parse_doc_from_index(content):
    ls = content.split("\n")
    see_text = False
    doc = ""
    for l in ls:
        l = l.replace("\n", "").strip()
        if "<TEXT>" in l:
            see_text = True
        elif "</TEXT>" in l:
            break
        elif see_text:
            if l == "<P>" or l == "</P>":
                continue
            doc += l + " "
    return doc.strip()
