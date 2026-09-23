package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.upms.api.entity.SysFile;
import com.lotus.bixi.common.oss.core.FileProperties;
import com.lotus.bixi.common.oss.core.FileTemplate;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.upms.mapper.SysFileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysFileServiceImplCompensationTest {

    @Mock
    SysFileMapper mapper;

    @Mock
    FileTemplate fileTemplate;

    @Mock
    FileProperties properties;

    @Mock
    PermissionService permissionService;

    @InjectMocks
    SysFileServiceImpl service;

    @Test
    void databaseFailureRemovesTheAlreadyStoredObject() throws Exception {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(properties.getBucket()).thenReturn("private");
        doThrow(new IllegalStateException("database unavailable"))
                .when(mapper).insert(any(SysFile.class));

        MockMultipartFile upload = new MockMultipartFile("file", "secret.txt", "text/plain",
                "secret".getBytes());
        com.lotus.bixi.common.core.util.R<?> result = service.uploadFile(upload);

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getMsg()).isEqualTo("file_upload_failed");
        verify(fileTemplate).removeObject(eq("private"), anyString());
    }
}
