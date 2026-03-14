package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.entity.WfApprovalRecord;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;

import java.util.List;

public interface ApprovalRecordService extends IService<WfApprovalRecord> {

    void saveRecord(WfApprovalRecord record);

    List<ApprovalRecordVO> listByProcessInstanceId(String processInstanceId);

}
