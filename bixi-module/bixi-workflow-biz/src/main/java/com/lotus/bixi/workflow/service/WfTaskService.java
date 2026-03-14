package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.workflow.api.dto.TaskCommentDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.vo.TaskVO;

public interface WfTaskService {

    IPage<TaskVO> todoPage(Page page, Long userId);

    IPage<TaskVO> donePage(Page page, Long userId);

    void complete(TaskCompleteDTO dto);

    void reject(TaskRejectDTO dto);

    void transfer(TaskTransferDTO dto);
    
    void delegate(TaskTransferDTO dto);

    TaskVO getById(String taskId);

    void claim(String taskId, Long userId);
    
    void unclaim(String taskId);
    
    Object getComments(String taskId);
    
    void addComment(TaskCommentDTO dto);

}
