package com.chiwanpark.sample.lucene.nrt;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Controller
public class WebController {

    @Autowired NRTLuceneService luceneService;

    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public Map<String, Object> queryPosts(@RequestParam("keyword") String keyword) {
        Map<String, Object> result = new HashMap<String, Object>();

        Query query = new TermQuery(new Term("keyword", keyword));

        long startTime = System.currentTimeMillis();
        List<Document> searchResult = luceneService.search(query, new HashSet<SortField>(), 50);
        List<Map<String, String>> convertedResult = new ArrayList<Map<String, String>>(searchResult.size());

        for (Document document : searchResult) {
            Map<String, String> converted = new HashMap<String, String>();

            converted.put("keyword", document.get("keyword"));
            converted.put("value", document.get("value"));

            convertedResult.add(converted);
        }

        long endTime = System.currentTimeMillis();

        result.put("searchCount", searchResult.size());
        result.put("searchResult", convertedResult);
        result.put("timeConsumed", (endTime - startTime) / 1000.0);

        return result;
    }

    @RequestMapping(value = "/add", method = RequestMethod.GET)
    public Map<String, Object> addPost(
            @RequestParam("keyword") String keyword,
            @RequestParam("value") String value) {
        Map<String, Object> result = new HashMap<String, Object>();

        Document document = new Document();

        document.add(new StringField("keyword", keyword, Field.Store.YES));
        document.add(new StringField("value", value, Field.Store.YES));

        long startTime = System.currentTimeMillis();
        luceneService.addDocument(document);
        long endTime = System.currentTimeMillis();

        result.put("timeConsumed", (endTime - startTime) / 1000.0);
        result.put("keyword", keyword);
        result.put("value", value);

        return result;
    }

}
