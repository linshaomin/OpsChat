package com.opschat.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingOutput;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.opschat.config.DashScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class VectorEmbeddingService {

    @Autowired
    private DashScopeProperties dashScopeProperties;

    @Value("${opschat.rag.top-k:3}")
    private int topK;

    private TextEmbedding textEmbedding;

    @PostConstruct
    public void init() {
        String apiKey = dashScopeProperties.getApiKey();
        String model = dashScopeProperties.getEmbeddingModel();

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your-api-key-here")) {
            log.error("API Key 未正确配置！当前值: {}", apiKey);
            throw new IllegalStateException("请设置环境变量 DASHSCOPE_API_KEY 或在 application.yml 中配置正确的 API Key");
        }

        String maskedKey = apiKey.length() > 8 ?
                apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4) :
                "***";
        log.info("API Key 已加载: {}", maskedKey);

        Constants.apiKey = apiKey;

        textEmbedding = new TextEmbedding();

        log.info("阿里云 DashScope Embedding 服务初始化完成，模型: {}", model);
    }

    public List<Float> generateEmbedding(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                log.warn("内容为空，无法生成向量");
                throw new IllegalArgumentException("内容不能为空");
            }

            log.debug("开始生成向量嵌入, 内容长度: {} 字符", content.length());

            if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
                log.warn("检测到 Constants.apiKey 为空，重新设置");
                Constants.apiKey = dashScopeProperties.getApiKey();
            }

            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .model(dashScopeProperties.getEmbeddingModel())
                    .texts(Collections.singletonList(content))
                    .build();

            TextEmbeddingResult result = textEmbedding.call(param);

            List<Float> floatEmbedding = getFloats(result);

            log.info("成功生成向量嵌入, 内容长度: {} 字符, 向量维度: {}",
                    content.length(), floatEmbedding.size());

            return floatEmbedding;

        } catch (NoApiKeyException e) {
            log.error("API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            log.error("生成向量嵌入失败, 内容长度: {}", content != null ? content.length() : 0, e);
            throw new RuntimeException("生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    private List<Float> getFloats(TextEmbeddingResult result) {
        if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
            throw new RuntimeException("DashScope API 返回空结果");
        }

        TextEmbeddingOutput output = result.getOutput();
        List<TextEmbeddingResultItem> embeddings = output.getEmbeddings();

        if (embeddings.isEmpty()) {
            throw new RuntimeException("DashScope API 返回空向量列表");
        }

        List<Double> embeddingDoubles = embeddings.get(0).getEmbedding();

        List<Float> floatEmbedding = new ArrayList<>(embeddingDoubles.size());
        for (Double value : embeddingDoubles) {
            floatEmbedding.add(value.floatValue());
        }
        return floatEmbedding;
    }

    public List<Float> generateQueryVector(String query) {
        return generateEmbedding(query);
    }
}