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

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xushu.rag.annotation.Loggable;
import com.xushu.rag.common.ApplicationConstant;
import com.xushu.rag.context.BaseContext;

import cn.hutool.json.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
@Tag(name = "AiRagController", description = "Rag接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/ai")
public class AiRagController {

    // 对话代理
    ChatClient chatClient;
    VectorStore vectorStore;


    //系统提示此，隐式的，全局的，所有对话都生效
    private static final String DEFAULT_SYSTEM_PROMPT = """
                        你是"moko"知识库系统的对话助手，请以乐于助人的方式进行对话，
                        今天的日期：{current_data}
                        """;


    public AiRagController(ChatModel  chatModel, ChatMemory chatMemory,
                           VectorStore vectorStore) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(chatMemory).build(), //对话记忆功能
                        SimpleLoggerAdvisor.builder().build() // 日志记录器，打印对话内容
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
                .system(a -> a.param("current_data", LocalDate.now().toString())) //传入当前日期
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId)) //传入用户ID，用于区分不同用户的对话
                .advisors(QuestionAnswerAdvisor
                        .builder(vectorStore) //添加向量数据库
                        //检索向量数据库，设置检索分数，返回最相似的前5条
                        .searchRequest(
                                SearchRequest.builder().query(message).similarityThreshold(0.1).topK(5)
                                //.filterExpression() 这里可以设置向量数据库的过滤条件，符合条件的再做相似度匹配
                                .build()
                        )
                        .build())
                .stream()// 流式方式，返回的信息是一个字一个字地返回，边生成边返回
                .content();
    }


}

