package com.lotus.bixi.common.feign.sentinel.handle;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class GlobalBizExceptionHandlerTest {

    @Test
    void handlesBrokenPipeWithoutTryingToWriteJsonIntoSseResponse() throws Exception {
        GlobalBizExceptionHandler handler = new GlobalBizExceptionHandler();
        assertThat(handler.handleGlobalException(new IOException("Broken pipe"))).isNull();

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BrokenPipeController())
                .setControllerAdvice(new GlobalBizExceptionHandler())
                .build();

        var result = mockMvc.perform(get("/sse")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResolvedException()).isInstanceOf(IOException.class);
    }

    @RestController
    static class BrokenPipeController {

        @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        String stream() throws IOException {
            throw new IOException("Broken pipe");
        }
    }
}
