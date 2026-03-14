package com.lotus.bixi.ai.controller;

import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.core.util.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    public R<ChatVO> chat(@RequestBody @Valid ChatDTO dto) {
        return R.ok(chatService.chat(dto));
    }

    @GetMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    public Flux<String> streamChat(@Valid ChatDTO dto) {
        return chatService.streamChat(dto);
    }

    @PostMapping("/rag")
    @Operation(summary = "RAG对话")
    public R<ChatVO> ragChat(@RequestBody @Valid ChatDTO dto) {
        return R.ok(chatService.ragChat(dto));
    }

    @PostMapping("/documents")
    @Operation(summary = "添加文档")
    public R<Void> addDocument(@RequestBody @Valid DocumentDTO dto) {
        vectorStoreService.addDocument(dto);
        return R.ok();
    }

    @PostMapping("/documents/batch")
    @Operation(summary = "批量添加文档")
    public R<Void> addDocuments(@RequestBody @Valid List<DocumentDTO> dtos) {
        vectorStoreService.addDocuments(dtos);
        return R.ok();
    }

    @PostMapping("/search")
    @Operation(summary = "相似度搜索")
    public R<List<DocumentVO>> search(@RequestBody @Valid SearchDTO dto) {
        return R.ok(vectorStoreService.similaritySearch(dto));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "删除文档")
    public R<Void> deleteDocument(@PathVariable Long id) {
        vectorStoreService.deleteDocument(id);
        return R.ok();
    }
}
