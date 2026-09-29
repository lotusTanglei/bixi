package com.lotus.bixi.upms.controller;

import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.mq.reliable.Idempotent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SysFileControllerSecurityTest {

    @Test
    void privateDownloadIsNotMarkedAsAnInnerUnauthenticatedEndpoint() throws Exception {
        Method method = SysFileController.class.getMethod("file", String.class, String.class,
                jakarta.servlet.http.HttpServletResponse.class);

        Inner inner = method.getAnnotation(Inner.class);

        assertThat(inner).isNull();
    }

    @Test
    void uploadRequiresPermissionAndWritesAnAuditRecord() throws Exception {
        Method method = SysFileController.class.getMethod("upload", org.springframework.web.multipart.MultipartFile.class);

        assertThat(method.getAnnotation(HasPermission.class).value()).containsExactly("sys_file_add");
        assertThat(method.getAnnotation(SysLog.class).value()).isEqualTo("上传文件");
    }

    @Test
    void ordinaryBusinessWritesExposeTheSharedIdempotencyProtocol() throws Exception {
        Method method = SysNoticeController.class.getMethod("save", com.lotus.bixi.upms.api.vo.SysNoticeVO.class);

        assertThat(method.getAnnotation(Idempotent.class).scope()).isEqualTo("notice.save");
    }
}
