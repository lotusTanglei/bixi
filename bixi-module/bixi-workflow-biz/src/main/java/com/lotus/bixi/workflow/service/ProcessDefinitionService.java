package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.ProcessDefinitionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProcessDefinitionService extends IService<WfProcessDefinition> {

    ProcessDefinitionVO deployDemo();

    /** Explicitly deploys the next demo definition version; existing instances are untouched. */
    ProcessDefinitionVO deployDemoV3();

    ProcessDefinitionVO deploy(MultipartFile file, String name, String category, String formKey);

    List<ProcessDefinitionVO> listDefinitions(ProcessQueryDTO query);

    List<ProcessDefinitionVO> listStartableDefinitions(ProcessQueryDTO query);

    List<ProcessDefinitionVO> listLatestVersions();

    ProcessDefinitionVO getByKey(String processKey);

    ProcessDefinitionVO getByProcessKey(String processKey);

    boolean suspend(String processDefinitionId);

    boolean activate(String processDefinitionId);

    byte[] getDiagram(String processDefinitionId);

    FormRenderVO getFormByProcessKey(String processKey);

    FormRenderVO getFormByDefinitionId(String processDefinitionId);

    FormRenderVO getStartForm(String processDefinitionId);

}
