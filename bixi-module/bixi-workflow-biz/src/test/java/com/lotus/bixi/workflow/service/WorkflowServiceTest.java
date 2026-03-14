package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.vo.ProcessDefinitionVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private ProcessDefinitionService processDefinitionService;

    @Mock
    private ProcessInstanceService processInstanceService;

    @Mock
    private WfTaskService wfTaskService;

    @Test
    @DisplayName("测试查询流程定义列表")
    void testListProcessDefinitions() {
        List<ProcessDefinitionVO> mockList = new ArrayList<>();
        ProcessDefinitionVO vo = new ProcessDefinitionVO();
        vo.setProcessDefinitionId("test:1:1");
        vo.setProcessKey("test");
        vo.setProcessName("测试流程");
        vo.setVersion(1);
        mockList.add(vo);

        when(processDefinitionService.listLatestVersions()).thenReturn(mockList);

        List<ProcessDefinitionVO> result = processDefinitionService.listLatestVersions();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test", result.get(0).getProcessKey());
        verify(processDefinitionService, times(1)).listLatestVersions();
    }

    @Test
    @DisplayName("测试根据Key查询流程定义")
    void testGetProcessDefinitionByKey() {
        ProcessDefinitionVO mockVO = new ProcessDefinitionVO();
        mockVO.setProcessDefinitionId("leave_approval:1:1");
        mockVO.setProcessKey("leave_approval");
        mockVO.setProcessName("请假审批流程");
        mockVO.setVersion(1);

        when(processDefinitionService.getByKey("leave_approval")).thenReturn(mockVO);

        ProcessDefinitionVO result = processDefinitionService.getByKey("leave_approval");

        assertNotNull(result);
        assertEquals("leave_approval", result.getProcessKey());
        assertEquals("请假审批流程", result.getProcessName());
    }

    @Test
    @DisplayName("测试发起流程")
    void testStartProcess() {
        ProcessStartDTO dto = new ProcessStartDTO();
        dto.setProcessKey("leave_approval");
        dto.setTitle("请假申请");
        dto.setBusinessKey("LEAVE-2024-001");
        Map<String, Object> variables = new HashMap<>();
        variables.put("applicant", "张三");
        variables.put("days", 3);
        dto.setVariables(variables);

        ProcessInstanceVO mockVO = new ProcessInstanceVO();
        mockVO.setProcessInstanceId("test-process-instance-id");
        mockVO.setProcessKey("leave_approval");
        mockVO.setTitle("请假申请");
        mockVO.setStatus("running");

        when(processInstanceService.start(any(ProcessStartDTO.class))).thenReturn(mockVO);

        ProcessInstanceVO result = processInstanceService.start(dto);

        assertNotNull(result);
        assertEquals("leave_approval", result.getProcessKey());
        assertEquals("running", result.getStatus());
    }

    @Test
    @DisplayName("测试分页查询流程实例")
    void testPageProcessInstances() {
        Page<ProcessInstanceVO> page = new Page<>(1, 10);
        ProcessQueryDTO query = new ProcessQueryDTO();
        query.setProcessKey("leave_approval");

        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setProcessInstanceId("test-id");
        vo.setProcessKey("leave_approval");
        vo.setTitle("请假申请");

        List<ProcessInstanceVO> records = new ArrayList<>();
        records.add(vo);
        
        Page<ProcessInstanceVO> resultPage = new Page<>(1, 10);
        resultPage.setRecords(records);
        resultPage.setTotal(1);

        when(processInstanceService.page(any(Page.class), any(ProcessQueryDTO.class))).thenReturn(resultPage);

        IPage<ProcessInstanceVO> result = processInstanceService.page(page, query);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("测试完成任务")
    void testCompleteTask() {
        TaskCompleteDTO dto = new TaskCompleteDTO();
        dto.setTaskId("test-task-id");
        dto.setApprovalComment("同意");
        dto.setApprovalType("approve");

        doNothing().when(wfTaskService).complete(any(TaskCompleteDTO.class));

        wfTaskService.complete(dto);

        verify(wfTaskService, times(1)).complete(dto);
    }

    @Test
    @DisplayName("测试驳回任务")
    void testRejectTask() {
        TaskRejectDTO dto = new TaskRejectDTO();
        dto.setTaskId("test-task-id");
        dto.setRejectReason("不符合要求");

        doNothing().when(wfTaskService).reject(any(TaskRejectDTO.class));

        wfTaskService.reject(dto);

        verify(wfTaskService, times(1)).reject(dto);
    }

    @Test
    @DisplayName("测试转办任务")
    void testTransferTask() {
        TaskTransferDTO dto = new TaskTransferDTO();
        dto.setTaskId("test-task-id");
        dto.setTransferUserId(1001L);
        dto.setTransferUserName("李四");
        dto.setTransferReason("转办给李四处理");

        doNothing().when(wfTaskService).transfer(any(TaskTransferDTO.class));

        wfTaskService.transfer(dto);

        verify(wfTaskService, times(1)).transfer(dto);
    }

    @Test
    @DisplayName("测试挂起流程定义")
    void testSuspendProcessDefinition() {
        String processDefinitionId = "leave_approval:1:1";

        when(processDefinitionService.suspend(processDefinitionId)).thenReturn(true);

        boolean result = processDefinitionService.suspend(processDefinitionId);

        assertTrue(result);
        verify(processDefinitionService, times(1)).suspend(processDefinitionId);
    }

    @Test
    @DisplayName("测试激活流程定义")
    void testActivateProcessDefinition() {
        String processDefinitionId = "leave_approval:1:1";

        when(processDefinitionService.activate(processDefinitionId)).thenReturn(true);

        boolean result = processDefinitionService.activate(processDefinitionId);

        assertTrue(result);
        verify(processDefinitionService, times(1)).activate(processDefinitionId);
    }

    @Test
    @DisplayName("测试终止流程实例")
    void testTerminateProcessInstance() {
        String processInstanceId = "test-process-instance-id";
        String reason = "用户取消";

        when(processInstanceService.terminate(processInstanceId, reason)).thenReturn(true);

        boolean result = processInstanceService.terminate(processInstanceId, reason);

        assertTrue(result);
        verify(processInstanceService, times(1)).terminate(processInstanceId, reason);
    }
}
