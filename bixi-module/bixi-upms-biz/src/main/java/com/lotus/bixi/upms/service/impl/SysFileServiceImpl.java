
package com.lotus.bixi.upms.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.amazonaws.services.s3.model.S3Object;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.upms.api.entity.SysFile;
import com.lotus.bixi.upms.mapper.SysFileMapper;
import com.lotus.bixi.upms.service.SysFileService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.oss.core.FileProperties;
import com.lotus.bixi.common.oss.core.FileTemplate;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 文件管理
 *
 * @author Luckly
 * @date 2025-01-01
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    private final FileTemplate fileTemplate;

    private final FileProperties properties;

    private final PermissionService permissionService;

    /**
     * 上传文件
     *
     * @param file
     * @return
     */
    @Override
    public R uploadFile(MultipartFile file) {
        String fileName = IdUtil.simpleUUID() + StrUtil.DOT + FileUtil.extName(file.getOriginalFilename());
        Map<String, String> resultMap = new HashMap<>(4);
        resultMap.put("bucketName", properties.getBucket());
        resultMap.put("fileName", fileName);
        resultMap.put("url", String.format("/admin/file/%s/%s", properties.getBucket(), fileName));

        boolean objectStored = false;
        try (InputStream inputStream = file.getInputStream()) {
            fileTemplate.putObject(properties.getBucket(), fileName, inputStream, file.getContentType());
            objectStored = true;
            // 文件管理数据记录,收集管理追踪文件
            fileLog(file, fileName);
        } catch (Exception e) {
            if (objectStored) {
                try {
                    fileTemplate.removeObject(properties.getBucket(), fileName);
                }
                catch (Exception compensationFailure) {
                    log.error("上传回滚删除对象失败: bucket={}, name={}", properties.getBucket(), fileName,
                            compensationFailure);
                }
            }
            log.error("上传失败: {}", e.getClass().getSimpleName());
            return R.failed("file_upload_failed");
        }
        return R.ok(resultMap);
    }

    /**
     * 读取文件
     *
     * @param bucket
     * @param fileName
     * @param response
     */
    @Override
    public void getFile(String bucket, String fileName, HttpServletResponse response) {
        SysFile file = baseMapper.selectOne(Wrappers.<SysFile>query()
                .eq("bucket", bucket)
                .eq("name", fileName));
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file_not_found");
        }
        if (!isDownloadAuthorized(file)) {
            throw new AccessDeniedException("file_access_denied");
        }
        try (S3Object s3Object = fileTemplate.getObject(bucket, fileName)) {
            response.setContentType("application/octet-stream; charset=UTF-8");
            IoUtil.copy(s3Object.getObjectContent(), response.getOutputStream());
        } catch (Exception e) {
            log.error("文件读取异常: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 删除文件
     *
     * @param id
     * @return
     */
    @Override
    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteFile(Long id) {
        SysFile file = this.getById(id);
        if (Objects.isNull(file)) {
            return Boolean.FALSE;
        }
        if (!this.removeById(id)) {
            return Boolean.FALSE;
        }
        try {
            fileTemplate.removeObject(properties.getBucket(), file.getName());
            return Boolean.TRUE;
        }
        catch (Exception error) {
            throw new IllegalStateException("file_object_delete_failed", error);
        }
    }

    boolean isDownloadAuthorized(SysFile file) {
        if (file == null || !java.util.Objects.equals(file.getTenantId(), TenantContextHolder.get())) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof BixiUser user)) {
            return false;
        }
        return java.util.Objects.equals(file.getCreateBy(), user.getId())
                || permissionService.hasPermission("sys_file_view", "sys_file_del");
    }

    /**
     * 文件管理数据记录,收集管理追踪文件
     *
     * @param file     上传文件格式
     * @param fileName 文件名
     */
    private void fileLog(MultipartFile file, String fileName) {
        SysFile sysFile = new SysFile();
        sysFile.setName(fileName);
        sysFile.setOriginal(file.getOriginalFilename());
        sysFile.setSize(file.getSize());
        sysFile.setType(FileUtil.extName(file.getOriginalFilename()));
        sysFile.setBucket(properties.getBucket());
        this.save(sysFile);
    }

}
