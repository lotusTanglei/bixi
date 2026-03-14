package com.lotus.bixi.ai.controller;

import com.lotus.bixi.ai.api.dto.MessageDTO;
import com.lotus.bixi.ai.api.dto.ModelConfigDTO;
import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.api.vo.MessageVO;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;
import com.lotus.bixi.ai.api.vo.SessionVO;
import com.lotus.bixi.ai.service.MessageService;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.common.core.util.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(description = "ai", name = "AI服务模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiSessionController {

    private final SessionService sessionService;
    private final MessageService messageService;
    private final ModelConfigService modelConfigService;

    @GetMapping("/session/list")
    @Operation(summary = "获取会话列表")
    public R<List<SessionVO>> listSessions() {
        return R.ok(sessionService.listSessions());
    }

    @PostMapping("/session")
    @Operation(summary = "创建会话")
    public R<SessionVO> createSession(@RequestBody @Valid SessionDTO dto) {
        return R.ok(sessionService.createSession(dto));
    }

    @PutMapping("/session")
    @Operation(summary = "更新会话")
    public R<SessionVO> updateSession(@RequestBody @Valid SessionDTO dto) {
        return R.ok(sessionService.updateSession(dto));
    }

    @DeleteMapping("/session/{id}")
    @Operation(summary = "删除会话")
    public R<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return R.ok();
    }

    @GetMapping("/session/{id}")
    @Operation(summary = "获取会话详情")
    public R<SessionVO> getSession(@PathVariable Long id) {
        return R.ok(sessionService.getSession(id));
    }

    @GetMapping("/message/list/{sessionId}")
    @Operation(summary = "获取消息列表")
    public R<List<MessageVO>> listMessages(@PathVariable Long sessionId) {
        return R.ok(messageService.listMessages(sessionId));
    }

    @PostMapping("/message")
    @Operation(summary = "发送消息")
    public R<MessageVO> sendMessage(@RequestBody @Valid MessageDTO dto) {
        return R.ok(messageService.sendMessage(dto));
    }

    @PostMapping("/message/rag")
    @Operation(summary = "发送RAG消息")
    public R<MessageVO> sendRagMessage(@RequestBody @Valid MessageDTO dto) {
        return R.ok(messageService.sendRagMessage(dto));
    }

    @DeleteMapping("/message/{sessionId}")
    @Operation(summary = "删除会话消息")
    public R<Void> deleteMessages(@PathVariable Long sessionId) {
        messageService.deleteMessages(sessionId);
        return R.ok();
    }

    @GetMapping("/config")
    @Operation(summary = "获取模型配置")
    public R<ModelConfigVO> getConfig() {
        return R.ok(modelConfigService.getConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "更新模型配置")
    public R<Void> updateConfig(@RequestBody @Valid ModelConfigDTO dto) {
        modelConfigService.updateConfig(dto);
        return R.ok();
    }
}
