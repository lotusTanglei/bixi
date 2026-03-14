package com.lotus.bixi.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.entity.WfApprovalRecord;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.mapper.WfApprovalRecordMapper;
import com.lotus.bixi.workflow.service.ApprovalRecordService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ApprovalRecordServiceImpl extends ServiceImpl<WfApprovalRecordMapper, WfApprovalRecord> implements ApprovalRecordService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRecord(WfApprovalRecord record) {
        this.save(record);
    }

    @Override
    public List<ApprovalRecordVO> listByProcessInstanceId(String processInstanceId) {
        List<WfApprovalRecord> records = this.lambdaQuery()
                .eq(WfApprovalRecord::getProcessInstanceId, processInstanceId)
                .orderByAsc(WfApprovalRecord::getApprovalTime)
                .list();

        return records.stream().map(record -> {
            ApprovalRecordVO vo = new ApprovalRecordVO();
            BeanUtils.copyProperties(record, vo);
            return vo;
        }).collect(Collectors.toList());
    }

}
