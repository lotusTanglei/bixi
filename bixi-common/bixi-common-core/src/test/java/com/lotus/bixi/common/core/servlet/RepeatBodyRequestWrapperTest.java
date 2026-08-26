package com.lotus.bixi.common.core.servlet;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatBodyRequestWrapperTest {

    @Test
    void exposesUpdatedParametersThroughAllServletAccessors() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("username", "admin");
        request.addParameter("password", "encrypted");

        RepeatBodyRequestWrapper wrapper = new RepeatBodyRequestWrapper(request);
        wrapper.getParameterMap().put("password", new String[]{"decrypted"});

        assertThat(wrapper.getParameter("password")).isEqualTo("decrypted");
        assertThat(wrapper.getParameterValues("password")).containsExactly("decrypted");
        assertThat(Collections.list(wrapper.getParameterNames()))
                .containsExactlyInAnyOrder("username", "password");
    }

}
