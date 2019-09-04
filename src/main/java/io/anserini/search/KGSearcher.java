package io.anserini.search;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.anserini.index.generator.LuceneDocumentGenerator;
import io.anserini.index.generator.TweetGenerator;
import io.anserini.rerank.RerankerCascade;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.ScoredDocuments;
import io.anserini.rerank.lib.Rm3Reranker;
import io.anserini.rerank.lib.ScoreTiesAdjusterReranker;
import io.anserini.search.query.BagOfWordsQueryGenerator;
import io.anserini.util.AnalyzerUtils;
import io.anserini.analysis.TweetAnalyzer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

public class KGSearcher implements Closeable {

  public static final String FIELD_URI = "title";
  public static final String FIELD_PRIOR = "prior";
  public static final String FIELD_ALIAS = "alias";
  public static final String MATCH = "match";
  public static final String PREFIX = "prefix";
  public static final String FUZZY = "fuzzy";
  public static final Sort BREAK_SCORE_TIES_BY_DOCID =
      new Sort(SortField.FIELD_SCORE, new SortField(LuceneDocumentGenerator.FIELD_ID, SortField.Type.STRING_VAL));
  
  private final IndexReader reader;
  private boolean isRerank;
  private RerankerCascade cascade;
  private Similarity similarity;
  private Analyzer analyzer;
  private String language;

  protected class Result {
    public String docid;
    public int ldocid;
    public String uri;
    public float score;
    public String content;
    public String prior;
    public String alias;

    public Result(String docid, int ldocid, String uri, float score, String content, String prior, String alias) {
      this.docid = docid;
      this.ldocid = ldocid;
      this.uri = uri;
      this.score = score;
      this.content = content;
      this.prior = prior;
      this.alias = alias;
    }
  }


  public KGSearcher(String indexDir) throws IOException {
    Path indexPath = Paths.get(indexDir);
    if (!Files.exists(indexPath) || !Files.isDirectory(indexPath) || !Files.isReadable(indexPath)) {
      throw new IllegalArgumentException(indexDir + " does not exist or is not a directory");
    }

    this.reader = DirectoryReader.open(FSDirectory.open(indexPath));
    // this.similarity = new LMDirichletSimilarity(1000.0f);
    setBM25Similarity(1.2f, 0.75f);
    this.isRerank = false;
    this.analyzer = new EnglishAnalyzer();
    setDefaultReranker();
  }

  public void setLanguage(String language) {
    this.language = language;
    this.analyzer = language.equals("zh")? new CJKAnalyzer() : this.analyzer;
  }

  public void setDefaultReranker() {
    isRerank = false;
    cascade = new RerankerCascade();
    cascade.add(new ScoreTiesAdjusterReranker());
  }

  public void setBM25Similarity(float k1, float b) {
    this.similarity = new BM25Similarity(k1, b);
  }

  public Result[] search(String q, int k) throws IOException {
    return search(q, LuceneDocumentGenerator.FIELD_BODY, true, null, k);
  }

  public Result[] search(String q, String field, boolean norm, String queryType, int k) throws IOException {
    if (norm) {
      Query query = new BagOfWordsQueryGenerator().buildQuery(field, analyzer, q);
      List<String> queryTokens = AnalyzerUtils.tokenize(analyzer, q);

      return search(query, queryTokens, q, k);
    } else {
      if (queryType.equals(MATCH)) {
        List<String> queryTokens = AnalyzerUtils.tokenize(analyzer, q);
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        for (String token: queryTokens) {
          builder.add(new Term(field, token));
        }
        PhraseQuery query = builder.build();
        return search(query, null, q, k);
      } else if (queryType.equals(PREFIX)) {
        PrefixQuery query = new PrefixQuery(new Term(field, q));
        return search(query, null, q, k);
      } else if (queryType.equals(FUZZY)) {
        int autoEditDist = 2;
        if (q.length() <= 1) autoEditDist = 0;
        if (q.length() >1 && q.length() <=5) autoEditDist = 1;
        Query query = new FuzzyQuery(new Term(field, q), autoEditDist);
        return search(query, null, q, k);
      } else {
       throw new IllegalArgumentException("queryType " + queryType +
        " is not supported");
      }
        
    }
  }


  protected Result[] search(Query query, List<String> queryTokens, String queryString, int k) throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);

    searcher.setSimilarity(similarity);

    SearchArgs searchArgs = new SearchArgs();
    searchArgs.arbitraryScoreTieBreak = false;
    searchArgs.hits = k;

    TopDocs rs = new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[]{});
    RerankerContext context;
    rs = searcher.search(query, isRerank ?
      searchArgs.rerankcutoff : k, BREAK_SCORE_TIES_BY_DOCID, true);
    
    context = new RerankerContext<>(searcher, null, query, null, queryString, queryTokens, null, searchArgs);

    ScoredDocuments hits = cascade.run(ScoredDocuments.fromTopDocs(rs, searcher), context);

    Result[] results = new Result[hits.ids.length];
    for (int i = 0; i < hits.ids.length; i++) {
      Document doc = hits.documents[i];
      String docid = doc.getField(LuceneDocumentGenerator.FIELD_ID).stringValue();
      IndexableField field = doc.getField(LuceneDocumentGenerator.FIELD_RAW);
      String content = field == null ? null : field.stringValue();
      IndexableField uriField = doc.getField(FIELD_URI);
      String uri = uriField == null ? null : uriField.stringValue();
      IndexableField priorField = doc.getField(FIELD_PRIOR);
      String prior = priorField == null ? null : priorField.stringValue();
      IndexableField aliasField = doc.getField(FIELD_ALIAS);
      String alias = aliasField == null ? null : aliasField.stringValue();


      results[i] = new Result(docid, hits.ids[i], uri, hits.scores[i], content, prior, alias);
    }
    return results;
  }

  

  @Override
  public void close() throws IOException {
    this.reader.close();
  }
}