package com.lotus.bixi.workflow.service;

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

    ProcessInstanceVO start(ProcessStartDTO dto);

    ProcessInstanceVO getById(String processInstanceId);

    IPage<ProcessInstanceVO> page(Page page, ProcessQueryDTO query);

    IPage<ProcessInstanceVO> myPage(Page page, ProcessQueryDTO query);

    boolean terminate(String processInstanceId, String reason);

    boolean suspend(String processInstanceId);

    boolean activate(String processInstanceId);

    String getProcessDiagram(String processInstanceId);

    List<ApprovalRecordVO> getApprovalHistory(String processInstanceId);

}
