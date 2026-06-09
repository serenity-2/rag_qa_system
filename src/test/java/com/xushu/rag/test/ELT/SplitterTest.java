package com.xushu.rag.test.ELT;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class SplitterTest {

    // 只要token数合理就行
    // 不要想着严格按照主题来分 （企业级知识库 各式各样的文档资料）
    @Test
    public void testTokenTextSplitter(@Value("classpath:rag/terms-of-service.txt") Resource resource) {
        TextReader textReader = new TextReader(resource);
       List<Document> documents = textReader.read();

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> apply = splitter.apply(documents);

        apply.forEach(System.out::println);
    }

    @Test
    public void testChineseTokenTextSplitter(@Value("classpath:rag/terms-of-service.txt") Resource resource) {
        TextReader textReader = new TextReader(resource);
        List<Document> documents = textReader.read();

        ChineseTokenTextSplitter splitter = new ChineseTokenTextSplitter();
        List<Document> apply = splitter.apply(documents);

        apply.forEach(System.out::println);
    }


    @Test
    public void testKeywordMetadataEnricher(
            @Autowired VectorStore vectorStore,
            @Autowired DashScopeChatModel chatModel,
            @Value("classpath:rag/terms-of-service.txt") Resource resource) {
        TextReader textReader = new TextReader(resource);
        textReader.getCustomMetadata().put("filename", resource.getFilename());
        List<Document> documents = textReader.read();


        ChineseTokenTextSplitter splitter = new ChineseTokenTextSplitter();
        documents = splitter.apply(documents);

        KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(chatModel, 5);
        documents=  enricher.apply(documents);
       /* KeywordMetadataEnricher.KEYWORDS_TEMPLATE= """
                给我按照我提供的内容{context_str},生成%s个关键字；
                允许的关键字有这些：
                ['退票','预定']
                只允许在这个关键字范围进行选择。
                """;*/
        vectorStore.add(documents);

        documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .filterExpression("filename in ('退票')")
                        // 过滤元数据
                        .filterExpression("excerpt_keywords in ('退票')")
                        .build());

        for (Document document : documents) {
            System.out.println(document.getText());
            System.out.println(document.getText().length());
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public VectorStore vectorStore(DashScopeEmbeddingModel embeddingModel) {
            return SimpleVectorStore.builder(embeddingModel).build();
        }
    }



    @Test
    public void testSummaryMetadataEnricher(
            @Autowired DashScopeChatModel chatModel,
            @Value("classpath:rag/terms-of-service.txt") Resource resource) {
        // 读取
        TextReader textReader = new TextReader(resource);
        textReader.getCustomMetadata().put("filename", resource.getFilename());
        List<Document> documents = textReader.read();


        // 分隔
        ChineseTokenTextSplitter splitter = new ChineseTokenTextSplitter(130,10,5,10000,true);
        List<Document> apply = splitter.apply(documents);

        // 摘要总结转换器  依赖大模型能力进行总结
        SummaryMetadataEnricher enricher = new SummaryMetadataEnricher(chatModel,
                List.of(SummaryMetadataEnricher.SummaryType.PREVIOUS,
                        SummaryMetadataEnricher.SummaryType.CURRENT,
                        SummaryMetadataEnricher.SummaryType.NEXT));


        apply = enricher.apply(apply);
        System.out.println(apply);
    }


    @Test
    public void testTextSplitter( ) {

        Document doc1 = new Document("This is a long piece of text that needs to be split into smaller chunks for processing.",
                Map.of("source", "example.txt"));
        Document doc2 = new Document("Another document with content that will be split based on token count.",
                Map.of("source", "example2.txt"));

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(List.of(doc1, doc2));

        for (Document doc : splitDocuments) {
            System.out.println("Chunk: " + doc.getText());
            System.out.println("Metadata: " + doc.getMetadata());
        }
    }

}
