package com.lotus.bixi.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProcessInstanceControllerTest {

    @Mock
    private ProcessInstanceService processInstanceService;

    @InjectMocks
    private ProcessInstanceController processInstanceController;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testStart() {
        ProcessStartDTO startDTO = new ProcessStartDTO();
        startDTO.setProcessKey("testKey");
        
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setProcessInstanceId("123");
        
        when(processInstanceService.start(any(ProcessStartDTO.class))).thenReturn(vo);

        R<ProcessInstanceVO> result = processInstanceController.start(startDTO);
        
        assertNotNull(result);
        assertEquals(0, result.getCode()); // Assuming R.ok() sets code to 0
        assertEquals("123", result.getData().getProcessInstanceId());
    }
}
