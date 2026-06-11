package com.xushu.rag.controller;

/**
 * @Title: KnowledgeController
 * @Author Xushu
 * @Package com.Xushu.rag.controller
 * @Date 2025/2/8 20:35
 * @description: 知识库
 */

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xushu.rag.common.ApplicationConstant;
import com.xushu.rag.common.BaseResponse;
import com.xushu.rag.common.ErrorCode;
import com.xushu.rag.common.ResultUtils;
import com.xushu.rag.entity.AliOssFile;
import com.xushu.rag.pojo.dto.QueryFileDTO;
import com.xushu.rag.service.AliOssFileService;
import com.xushu.rag.utils.AliOssUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "KnowledgeController", description = "知识库管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge")
public class KnowledgeController {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private TokenTextSplitter tokenTextSplitter;


    @Autowired
    private AliOssFileService aliOssFileService;
    
    /**
     * 上传附件接口
     *
     *  1. 提供不同的分片策略
     *  2. 分片后的预览
     *
     *  text-to-sql:
     *  上传资料--->（用户选择是否聚合）----> tosql---->创建表、插入数据
     * @param
     * @return
     * @throws IOException
     */

    @Operation(summary = "upload", description = "上传附件接口")
    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    public BaseResponse upload(@RequestParam("file") List<MultipartFile> files) {
        if (files.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请上传文件");
        }
        for (MultipartFile file : files) {
            //上传oss
            try {
                //原始文件名
                String originalFilename = file.getOriginalFilename();
                //文件后缀
                String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                //随机文件名(用户上传的文件可能重复，避免覆盖)
                String objectName = UUID.randomUUID().toString().replace("-", "") + extension;
                //上传oss,返回文件访问路径
                String url = aliOssUtil.upload(file.getBytes(), objectName);
                
                //向量化
                //1.读取文件：txt pdf docx，doc
                Resource resource = file.getResource();
                //source — 来自 TikaDocumentReader 读取文件时，自动把文件名写入向量数据库的metadata字段：
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> documents = reader.read();
                
                //2.按token分块，底层使用jtokkit 库：就是 OpenAI 官方的 token 计算器
                //chunk_index、total_chunks、parent_document_id — 来自tokenTextSplitter.apply(documents) 切块时自动添加的。
                List<Document> splitDocuments = tokenTextSplitter.apply(documents);
                //3.向量化
                vectorStore.add(splitDocuments);
                //4.持久化到数据库
                long timeMillis = System.currentTimeMillis();
                aliOssFileService.save(AliOssFile.builder()
                        .fileName(originalFilename)
                        .url(url)
                        .vectorId(splitDocuments.get(0).getId())
                        .createTime(new Date(timeMillis))
                        .updateTime(new Date(timeMillis))
                        .build());
                    
            } catch (IOException | RuntimeException e) {
                log.error("文件上传失败", e);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "文件上传失败");
            }
            
        }
        return ResultUtils.success("文件上传成功");
    }


    @Operation(summary = "contents",description = "文件查询")
    @GetMapping("/contents")
    public BaseResponse queryFiles(QueryFileDTO request){
        if(request.getPage() == null || request.getPageSize() == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"page 或 pageSize为空");
        }
        return aliOssFileService.queryPage(request);
    }

    @Operation(summary = "delete",description = "文件删除")
    @DeleteMapping("/delete")
    public BaseResponse deleteFiles(@RequestParam List<Long> ids){
        return aliOssFileService.deleteFiles(ids);
    }


    @Operation(summary = "download",description = "文件下载")
    @GetMapping("/download")
    public BaseResponse downloadFiles(@RequestParam List<Long> ids){
        return aliOssFileService.downloadFiles(ids);
    }




}