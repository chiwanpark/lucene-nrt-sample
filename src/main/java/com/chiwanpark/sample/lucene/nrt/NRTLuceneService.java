package com.chiwanpark.sample.lucene.nrt;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TrackingIndexWriter;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class NRTLuceneService implements InitializingBean, DisposableBean {

    private Logger logger;

    private Directory indexDirectory;
    private Analyzer analyzer;

    private IndexWriter indexWriter;
    private TrackingIndexWriter trackingIndexWriter;

    private ReferenceManager<IndexSearcher> indexSearcherManager;
    private ControlledRealTimeReopenThread<IndexSearcher> indexReopenThread;

    private double maxStaleSec;
    private double minStaleSec;

    private long lastGeneration;

    private long commitCount;
    private long maxCommitCount;

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public void setIndexDirectory(Directory indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public void setMaxStaleSec(double maxStaleSec) {
        this.maxStaleSec = maxStaleSec;
    }

    public void setMinStaleSec(double minStaleSec) {
        this.minStaleSec = minStaleSec;
    }

    public void setMaxCommitCount(long maxCommitCount) {
        this.maxCommitCount = maxCommitCount;
    }

    @Override
    public void destroy() throws Exception {
        indexReopenThread.interrupt();
        indexReopenThread.close();

        indexWriter.commit();
        indexWriter.close();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        commitCount = 0;

        logger = LoggerFactory.getLogger(this.getClass());

        indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(Version.LUCENE_44, analyzer));
        trackingIndexWriter = new TrackingIndexWriter(indexWriter);

        indexSearcherManager = new SearcherManager(indexWriter, true, null);

        indexReopenThread = new ControlledRealTimeReopenThread<IndexSearcher>(trackingIndexWriter, indexSearcherManager, maxStaleSec, minStaleSec);

        indexReopenThread.start();
    }

    public void commit() {
        try {
            if (commitCount > maxCommitCount) {
                indexWriter.commit();
                logger.debug("Committed to Lucene index.");
                commitCount = 0;
            }

            ++commitCount;
        } catch (IOException e) {
            logger.error("Error in Lucene index commit work. {}", e.getMessage(), e);
        }
    }

    public void addDocument(final Document document) {
        try {
            lastGeneration = trackingIndexWriter.addDocument(document);
            logger.debug("Added document in Lucene index.");
        } catch (IOException e) {
            logger.error("Error in addition work. {}", e.getMessage(), e);
        } finally {
            commit();
        }
    }

    public void updateDocument(final Term term, final Document document) {
        try {
            lastGeneration = trackingIndexWriter.updateDocument(term, document);
            logger.debug("Updated document in Lucene index.");
        } catch (IOException e) {
            logger.error("Error in update work. {}", e.getMessage(), e);
        } finally {
            commit();
        }
    }

    public void deleteDocuments(final Term term) {
        try {
            lastGeneration = trackingIndexWriter.deleteDocuments(term);
            logger.debug("Deleted document in Lucene index.");
        } catch (IOException e) {
            logger.error("Error in deletion work. {}", e.getMessage(), e);
        } finally {
            commit();
        }
    }

    private IndexSearcher acquireSearcher() {
        IndexSearcher searcher = null;
        try {
            indexReopenThread.waitForGeneration(lastGeneration);
            searcher = indexSearcherManager.acquire();
        } catch (InterruptedException e) {
            logger.error("Index Reopen Thread is interrupted.");
        } catch (IOException e) {
            logger.error("Error in acquire index searcher.");
        }

        return searcher;
    }

    private void releaseSearcher(final IndexSearcher searcher) {
        try {
            indexSearcherManager.release(searcher);
        } catch (IOException e) {
            logger.error("Error in release index searcher.");
        }
    }

    public List<Document> search(final Query query, Set<SortField> sortFields, int numberOfResults) {
        IndexSearcher searcher = acquireSearcher();
        List<Document> results = null;

        Sort sort;
        TopDocs scoredDocs;

        try {
            if (!sortFields.isEmpty()) {
                sort = new Sort(sortFields.toArray(new SortField[sortFields.size()]));
                scoredDocs = searcher.search(query, numberOfResults, sort);
            } else {
                scoredDocs = searcher.search(query, numberOfResults);
            }

            results = new ArrayList<Document>(scoredDocs.totalHits);
            for (ScoreDoc scoreDoc : scoredDocs.scoreDocs) {
                Document document = searcher.doc(scoreDoc.doc);

                results.add(document);
            }
        } catch (IOException e) {
            logger.error("Error in Search Operation");
        }

        releaseSearcher(searcher);
        return results;
    }

    public void optimize() {
        try {
            indexWriter.forceMerge(1);
        } catch (IOException e) {
            logger.error("Error while optimizing index.");
        }
    }

}
