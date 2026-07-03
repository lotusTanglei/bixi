package com.lotus.bixi.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.annotation.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

/**
 * AI 服务控制器
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(description = "ai", name = "AI服务模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiController {

    private final ChatService chatService;
    private final VectorStoreService vectorStoreService;

    @PostMapping("/chat")
    @Operation(summary = "同步对话")
    @HasPermission("ai:chat:add")
    public R<ChatVO> chat(@RequestBody @Valid ChatDTO dto) {
        return R.ok(chatService.chat(dto));
    }

    @GetMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    @HasPermission("ai:chat:add")
    public Flux<String> streamChat(@Valid ChatDTO dto) {
        return chatService.streamChat(dto);
    }

    @PostMapping("/rag")
    @Operation(summary = "RAG对话")
    @HasPermission("ai:rag:add")
    public R<ChatVO> ragChat(@RequestBody @Valid ChatDTO dto) {
        return R.ok(chatService.ragChat(dto));
    }

    @PostMapping("/documents")
    @Operation(summary = "添加文档")
    @HasPermission("ai:document:add")
    public R<Void> addDocument(@RequestBody @Valid DocumentDTO dto) {
        vectorStoreService.addDocument(dto);
        return R.ok();
    }

    @PostMapping("/documents/batch")
    @Operation(summary = "批量添加文档")
    @HasPermission("ai:document:add")
    public R<Void> addDocuments(@RequestBody @Valid List<DocumentDTO> dtos) {
        vectorStoreService.addDocuments(dtos);
        return R.ok();
    }

    @GetMapping("/documents/page")
    @Operation(summary = "分页查询文档")
    @HasPermission("ai:document:view")
    public R<IPage<DocumentVO>> pageDocuments(Page<AiDocument> page,
                                              @RequestParam(value = "name", required = false) String name,
                                              @RequestParam(value = "title", required = false) String title) {
        return R.ok(vectorStoreService.pageDocuments(page, title != null ? title : name));
    }

    @GetMapping("/documents/list")
    @Operation(summary = "查询文档列表")
    @HasPermission("ai:document:view")
    public R<List<DocumentVO>> listDocuments(@RequestParam(value = "name", required = false) String name,
                                             @RequestParam(value = "title", required = false) String title) {
        return R.ok(vectorStoreService.listDocuments(title != null ? title : name));
    }

    @PostMapping("/documents/upload")
    @Operation(summary = "上传文档")
    @HasPermission("ai:document:add")
    public R<DocumentVO> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(vectorStoreService.uploadDocument(file));
    }

    @PostMapping("/search")
    @Operation(summary = "相似度搜索")
    @HasPermission("ai:document:view")
    public R<List<DocumentVO>> search(@RequestBody @Valid SearchDTO dto) {
        return R.ok(vectorStoreService.similaritySearch(dto));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "删除文档")
    @HasPermission("ai:document:del")
    public R<Void> deleteDocument(@PathVariable Long id) {
        vectorStoreService.deleteDocument(id);
        return R.ok();
    }
}
