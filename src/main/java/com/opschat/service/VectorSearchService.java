package com.opschat.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import com.opschat.config.MilvusProperties;
import com.opschat.constant.MilvusConstants;
import com.opschat.service.VectorEmbeddingService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class VectorSearchService {

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired
    private MilvusProperties milvusProperties;

    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            log.info("开始搜索相似文档, 查询: {}, topK: {}", query, topK);

            ensureCollectionLoaded();

            List<Float> queryVector = embeddingService.generateQueryVector(query);
            log.debug("查询向量生成成功, 维度: {}", queryVector.size());

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.IP)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"nprobe\":32}")
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("向量搜索失败: " + searchResponse.getMessage());
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResult result = new SearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());

                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }

                results.add(result);
            }

            log.info("搜索完成, 找到 {} 个相似文档", results.size());
            return results;

        } catch (Exception e) {
            log.error("搜索相似文档失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }

    private void ensureCollectionLoaded() {
        try {
            String collectionName = MilvusConstants.MILVUS_COLLECTION_NAME;

            LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<RpcStatus> response = milvusClient.loadCollection(loadParam);
            if (response.getStatus() != 0) {
                log.warn("集合加载警告: {}", response.getMessage());
            }

            log.debug("集合加载检查完成: {}", collectionName);

        } catch (Exception e) {
            log.warn("集合加载检查异常: {}", e.getMessage());
        }
    }

    @Setter
    @Getter
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private String metadata;
    }
}