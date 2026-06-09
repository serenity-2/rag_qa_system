/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.xushu.rag.controller;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.model.RerankModel;
import com.xushu.rag.annotation.Loggable;
import com.xushu.rag.common.ApplicationConstant;
import com.xushu.rag.common.ErrorCode;
import com.xushu.rag.context.BaseContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.JsonObjectSerializer;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
@Tag(name = "AiRagController", description = "Rag接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiRagController {

    // 对话代理
    ChatClient chatClient;
    VectorStore vectorStore;


    private static final String DEFAULT_SYSTEM_PROMPT = """
                        你是"XS"知识库系统的对话助手，请以乐于助人的方式进行对话，
                        今天的日期：{current_data}
                        """;


    public AiRagController(ChatModel  chatModel, ChatMemory chatMemory,
                           VectorStore vectorStore) {
        this.chatClient = ChatClient.builder(chatModel)
                // 隐式
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
        this.vectorStore=vectorStore;
    }

    @Operation(summary = "rag post", description = "Rag对话接口POST版本")
    @PostMapping(value = "/rag" )
    @Loggable
    public Flux<String> generatePost(@RequestParam(value = "message", defaultValue = "你好") String message) throws IOException {

        Long userId = BaseContext.getCurrentId();
        return chatClient.prompt()
                .user(message)
                .system(a -> a.param("current_data", LocalDate.now().toString()))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))
                .advisors(QuestionAnswerAdvisor
                        .builder(vectorStore)
                        .searchRequest(
                                SearchRequest.builder().query(message).similarityThreshold(0.1).topK(5).build()
                        )
                        .build())
                .stream()// 流式方式
                .content();
    }


}

