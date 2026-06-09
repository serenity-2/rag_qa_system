package com.xushu.rag.test.ELT;


import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.util.List;

@SpringBootTest
public class ReaderTest {

    @Test
    public void testReaderText(@Value("classpath:rag/terms-of-service.txt") Resource resource) {


        TextReader textReader = new TextReader(resource);
        List<Document> documents = textReader.read();

        for (Document document : documents) {
            System.out.println(document.getText());
        }
    }



}
