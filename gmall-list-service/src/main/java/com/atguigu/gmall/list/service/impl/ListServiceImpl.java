package com.atguigu.gmall.list.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.bean.SkuLsInfo;
import com.atguigu.gmall.bean.SkuLsParams;
import com.atguigu.gmall.bean.SkuLsResult;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.service.ListService;
import io.searchbox.client.JestClient;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.Update;
import io.searchbox.core.search.aggregation.MetricAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;
import org.apache.commons.beanutils.BeanUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import javax.swing.text.Highlighter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Service
public class ListServiceImpl implements ListService {

    @Autowired
    JestClient jestClient;

    @Autowired
    RedisUtil redisUtil;

    public static final String index_gmall="gmall";

    public static final String type_gmall="SkuInfo";


    public void saveSkuInfo(SkuLsInfo skuLsInfo){
       Index index=new Index.Builder(skuLsInfo).index(index_gmall).type(type_gmall).id(skuLsInfo.getId()).build();
        try {
            jestClient.execute(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;

    }

    private void sendSkuToList(SkuInfo skuInfo){
        SkuLsInfo skuLsInfo = new SkuLsInfo();

        try {
            BeanUtils.copyProperties(skuLsInfo,skuInfo);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }



    }






    @Test
    public void testEs() throws IOException {
        String query="{\n" +
                "  \"query\": {\n" +
                "    \"match\": {\n" +
                "      \"carList.name\": \"大众\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        Search search = new Search.Builder(query).addIndex("car_chn").addType("car").build();

        SearchResult result = jestClient.execute(search);

        List<SearchResult.Hit<HashMap, Void>> hits = result.getHits(HashMap.class);

        for (SearchResult.Hit<HashMap, Void> hit : hits) {
            HashMap source = hit.source;
            System.err.println("source = " + source);

        }

    }


    @Test
    public void testSearchQuery(){
        SkuLsParams skuLsParam=new SkuLsParams();
        skuLsParam.setCatalog3Id("61");
        skuLsParam.setValueId(new String[]{"6","9"});

        skuLsParam.setKeyword("gg");

        skuLsParam.setPageNo(2);

        skuLsParam.setPageSize(2);



        makeQueryStringForSearch(  skuLsParam);
    }


    public SkuLsResult searchSkuInfoList(SkuLsParams skuLsParams){
        String query = makeQueryStringForSearch(skuLsParams);

        Search search = new Search.Builder(query).addIndex(index_gmall).addType(type_gmall).build();
        SearchResult searchResult=null;
        try {
            searchResult = jestClient.execute(search);
        } catch (IOException e) {
            e.printStackTrace();
        }

        SkuLsResult skuLsResult = makeResultForSearch(skuLsParams, searchResult);
        return skuLsResult;

    }


    public String makeQueryStringForSearch(SkuLsParams skuLsParams){
        SearchSourceBuilder searchSourceBuilder =new SearchSourceBuilder();

        //复合查询
        BoolQueryBuilder boolQueryBuilder=new BoolQueryBuilder();

        if(skuLsParams.getKeyword()!=null){
            //关键词
            MatchQueryBuilder queryBuilder=new MatchQueryBuilder("skuName",skuLsParams.getKeyword());
            boolQueryBuilder.must(queryBuilder);

            //高亮
            HighlightBuilder highlightBuilder=new HighlightBuilder();
            highlightBuilder.preTags("<span style='color:red'>");
            highlightBuilder.postTags("</span>");
            highlightBuilder.field("skuName");
            searchSourceBuilder.highlight(highlightBuilder);

        }

        if(skuLsParams.getCatalog3Id()!=null){
            //三级分类过滤
            TermQueryBuilder termQueryBuilder=new TermQueryBuilder("catalog3Id",skuLsParams.getCatalog3Id());
            boolQueryBuilder.filter(termQueryBuilder);
        }

        if(skuLsParams.getValueId()!=null&&skuLsParams.getValueId().length>0){
            //平台属性过滤
            for (int i = 0; i < skuLsParams.getValueId().length; i++) {
                String valueId = skuLsParams.getValueId()[i];
                TermQueryBuilder termQueryBuilder = new TermQueryBuilder("skuAttrValueList.valueId", valueId);
                boolQueryBuilder.filter(termQueryBuilder);
            }

        }

        searchSourceBuilder.query(boolQueryBuilder);



        //分页
        int from=(skuLsParams.getPageNo()-1)*skuLsParams.getPageSize();

        searchSourceBuilder.from(from);
        searchSourceBuilder.size(skuLsParams.getPageSize());

        //排序
        searchSourceBuilder.sort("hotScore", SortOrder.DESC);

        //聚合
        TermsBuilder groupby_valueId = AggregationBuilders.terms("groupby_valueId").field("skuAttrValueList.valueId");
        searchSourceBuilder.aggregation(groupby_valueId);

        System.out.println("searchSourceBuilder.toString() = " + searchSourceBuilder.toString());
        return searchSourceBuilder.toString();

    }

    public SkuLsResult makeResultForSearch(SkuLsParams skuLsParams,SearchResult searchResult){
        SkuLsResult skuLsResult = new SkuLsResult();
        List<SkuLsInfo> skuInfoList = new ArrayList<>(skuLsParams.getPageSize());

        List<SearchResult.Hit<SkuLsInfo, Void>> hits = searchResult.getHits(SkuLsInfo.class);
        for (SearchResult.Hit<SkuLsInfo, Void> hit : hits) {
            SkuLsInfo skuLsInfo = hit.source;
            if(skuLsParams.getKeyword()!=null){
                List<String> list = hit.highlight.get("skuName");
                String skuNameHl = list.get(0);
                skuLsInfo.setSkuName(skuNameHl);

            }
            skuInfoList.add(skuLsInfo);
        }

        skuLsResult.setSkuLsInfoList(skuInfoList);
        skuLsResult.setTotal(searchResult.getTotal().intValue());

        MetricAggregation aggregations = searchResult.getAggregations();
        TermsAggregation groupby_valueId = aggregations.getTermsAggregation("groupby_valueId");
        List<TermsAggregation.Entry> buckets = groupby_valueId.getBuckets();

        List<String> valueIdList =new ArrayList<>(buckets.size());
        for (TermsAggregation.Entry bucket : buckets) {
            String valueId = bucket.getKey();
            valueIdList.add(valueId);
        }
        skuLsResult.setAttrValueIdList(valueIdList);

        return skuLsResult;
    }

    public void incrHotScore(String skuId){
        Jedis jedis = redisUtil.getJedis();
        int timesToEs=100;
        Double hotScore = jedis.zincrby("hotScore", 1, "skuId:" + skuId);
        if(hotScore%timesToEs==0){
            updateHotScore(skuId,  Math.round(hotScore));
        }

    }


    private void updateHotScore(String skuId,Long hotScore){
        String updateJson="{\n" +
                "   \"doc\":{\n" +
                "     \"hotScore\":"+hotScore+"\n" +
                "   }\n" +
                "}";

        Update update = new Update.Builder(updateJson).index("gmall").type("SkuInfo").id(skuId).build();
        try {
            jestClient.execute(update);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
















}
