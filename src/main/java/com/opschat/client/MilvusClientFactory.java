package com.opschat.client;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import com.opschat.config.MilvusProperties;
import com.opschat.constant.MilvusConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MilvusClientFactory {

    @Autowired
    private MilvusProperties milvusProperties;

    public MilvusServiceClient createClient() {
        MilvusServiceClient client = null;

        try {
            log.info("正在连接到 Milvus: {}:{}", milvusProperties.getHost(), milvusProperties.getPort());
            client = connectToMilvus();
            log.info("成功连接到 Milvus");

            if (!collectionExists(client, MilvusConstants.MILVUS_COLLECTION_NAME)) {
                log.info("collection '{}' 不存在，正在创建...", MilvusConstants.MILVUS_COLLECTION_NAME);
                createBizCollection(client);
                log.info("成功创建 collection '{}'", MilvusConstants.MILVUS_COLLECTION_NAME);

                createIndexes(client);
                log.info("成功创建索引");
            } else {
                log.info("collection '{}' 已存在", MilvusConstants.MILVUS_COLLECTION_NAME);
            }

            return client;

        } catch (Exception e) {
            log.error("创建 Milvus 客户端失败", e);
            if (client != null) {
                client.close();
            }
            throw new RuntimeException("创建 Milvus 客户端失败: " + e.getMessage(), e);
        }
    }

    private MilvusServiceClient connectToMilvus() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .withConnectTimeout(milvusProperties.getTimeout(), TimeUnit.MILLISECONDS);

        if (milvusProperties.getUsername() != null && !milvusProperties.getUsername().isEmpty()) {
            builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
        }

        return new MilvusServiceClient(builder.build());
    }

    private boolean collectionExists(MilvusServiceClient client, String collectionName) {
        R<Boolean> response = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("检查 collection 失败: " + response.getMessage());
        }

        return response.getData();
    }

    private void createBizCollection(MilvusServiceClient client) {
        CreateCollectionParam param = CreateCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withDescription("OpsChat 文档向量集合")
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .addFieldType(FieldType.newBuilder()
                        .withName("id")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(MilvusConstants.ID_MAX_LENGTH)
                        .withPrimaryKey(true)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName("vector")
                        .withDataType(DataType.FloatVector)
                        .withDimension(MilvusConstants.VECTOR_DIM)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName("content")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName("metadata")
                        .withDataType(DataType.JSON)
                        .build())
                .build();

        R<RpcStatus> response = client.createCollection(param);
        if (response.getStatus() != 0) {
            throw new RuntimeException("创建 collection 失败: " + response.getMessage());
        }
    }

    private void createIndexes(MilvusServiceClient client) {
        CreateIndexParam param = CreateIndexParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"nlist\":256}")
                .build();

        R<RpcStatus> response = client.createIndex(param);
        if (response.getStatus() != 0) {
            throw new RuntimeException("创建索引失败: " + response.getMessage());
        }
    }
}