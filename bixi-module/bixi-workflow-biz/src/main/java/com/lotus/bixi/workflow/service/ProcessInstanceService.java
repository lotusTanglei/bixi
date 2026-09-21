package com.lotus.bixi.workflow.service;

import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;

import java.util.List;

public interface ProcessInstanceService extends IService<WfProcessInstance> {

    WorkflowCommandVO getCommand(String requestId);

    ProcessInstanceVO start(ProcessStartDTO dto);

    ProcessInstanceVO getById(String processInstanceId);

    IPage<ProcessInstanceVO> page(Page page, ProcessQueryDTO query);

    IPage<ProcessInstanceVO> myPage(Page page, ProcessQueryDTO query);

    boolean terminate(String processInstanceId, String reason);

    boolean terminate(String processInstanceId, String reason, String requestId);

    boolean suspend(String processInstanceId);

    boolean suspend(String processInstanceId, String requestId);

    boolean activate(String processInstanceId);

    boolean activate(String processInstanceId, String requestId);

    String getProcessDiagram(String processInstanceId);

    List<ApprovalRecordVO> getApprovalHistory(String processInstanceId);

}
