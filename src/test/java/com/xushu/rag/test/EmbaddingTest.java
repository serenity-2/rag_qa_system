package com.xushu.rag.test;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

@SpringBootTest
public class EmbaddingTest {


    @Test
    public void testAliEmbadding(@Autowired DashScopeEmbeddingModel
                                      embeddingModel) {
        float[] embedded = embeddingModel.embed("我叫徐庶");
        System.out.println(embedded.length);
        System.out.println(Arrays.toString(embedded));

    }

}