package com.lotus.bixi.upms.api.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.api.dto.UserInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptPasswordButNeverSerializeIt() throws Exception {
        SysUser user = objectMapper.readValue("{\"username\":\"admin\",\"password\":\"secret\"}", SysUser.class);

        assertThat(user.getPassword()).isEqualTo("secret");
        assertThat(objectMapper.writeValueAsString(user)).doesNotContain("password", "secret");
    }

	@Test
	void shouldTransportEncodedPasswordOnlyThroughInternalUserInfo() throws Exception {
		SysUser user = new SysUser();
		user.setUsername("admin");
		user.setPassword("hidden");
		UserInfo userInfo = new UserInfo();
		userInfo.setSysUser(user);
		userInfo.setEncodedPassword("encoded-password");

		String json = objectMapper.writeValueAsString(userInfo);

		assertThat(json).doesNotContain("hidden");
		assertThat(json).contains("\"encodedPassword\":\"encoded-password\"");
		assertThat(objectMapper.readValue(json, UserInfo.class).getEncodedPassword())
				.isEqualTo("encoded-password");
	}

}
