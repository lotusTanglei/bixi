package com.lotus.bixi.common.workflow.util;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.Map;

/**
 * 工作流工具类
 *
 * @author bixi
 * @date 2025-01-01
 */
public final class WorkflowUtils {

    private WorkflowUtils() {
    }

    /**
     * 获取流程实例ID
     *
     * @param task 任务对象
     * @return 流程实例ID
     */
    public static String getProcessInstanceId(Task task) {
        if (task == null) {
            return null;
        }
        return task.getProcessInstanceId();
    }

    /**
     * 获取任务ID
     *
     * @param task 任务对象
     * @return 任务ID
     */
    public static String getTaskId(Task task) {
        if (task == null) {
            return null;
        }
        return task.getId();
    }

    /**
     * 获取流程变量
     *
     * @param runtimeService 运行时服务
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名
     * @return 变量值
     */
    public static Object getVariable(RuntimeService runtimeService, String processInstanceId, String variableName) {
        if (runtimeService == null || processInstanceId == null || variableName == null) {
            return null;
        }
        return runtimeService.getVariable(processInstanceId, variableName);
    }

    /**
     * 获取流程变量
     *
     * @param runtimeService 运行时服务
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名
     * @param clazz 变量类型
     * @param <T> 泛型类型
     * @return 变量值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getVariable(RuntimeService runtimeService, String processInstanceId, String variableName, Class<T> clazz) {
        Object value = getVariable(runtimeService, processInstanceId, variableName);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 设置流程变量
     *
     * @param runtimeService 运行时服务
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名
     * @param value 变量值
     */
    public static void setVariable(RuntimeService runtimeService, String processInstanceId, String variableName, Object value) {
        if (runtimeService == null || processInstanceId == null || variableName == null) {
            return;
        }
        runtimeService.setVariable(processInstanceId, variableName, value);
    }

    /**
     * 设置多个流程变量
     *
     * @param runtimeService 运行时服务
     * @param processInstanceId 流程实例ID
     * @param variables 变量Map
     */
    public static void setVariables(RuntimeService runtimeService, String processInstanceId, Map<String, Object> variables) {
        if (runtimeService == null || processInstanceId == null || variables == null) {
            return;
        }
        runtimeService.setVariables(processInstanceId, variables);
    }

    /**
     * 删除流程变量
     *
     * @param runtimeService 运行时服务
     * @param processInstanceId 流程实例ID
     * @param variableName 变量名
     */
    public static void removeVariable(RuntimeService runtimeService, String processInstanceId, String variableName) {
        if (runtimeService == null || processInstanceId == null || variableName == null) {
            return;
        }
        runtimeService.removeVariable(processInstanceId, variableName);
    }

    /**
     * 获取流程实例
     *
     * @param runtimeService 运行时服务
     * @param processInstanceId 流程实例ID
     * @return 流程实例
     */
    public static ProcessInstance getProcessInstance(RuntimeService runtimeService, String processInstanceId) {
        if (runtimeService == null || processInstanceId == null) {
            return null;
        }
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    /**
     * 判断流程实例是否结束
     *
     * @param runtimeService 运行时服务
     * @param processInstanceId 流程实例ID
     * @return 是否结束
     */
    public static boolean isProcessEnded(RuntimeService runtimeService, String processInstanceId) {
        ProcessInstance processInstance = getProcessInstance(runtimeService, processInstanceId);
        return processInstance == null;
    }

    /**
     * 获取任务
     *
     * @param taskService 任务服务
     * @param taskId 任务ID
     * @return 任务对象
     */
    public static Task getTask(TaskService taskService, String taskId) {
        if (taskService == null || taskId == null) {
            return null;
        }
        return taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
    }

    /**
     * 获取流程引擎
     *
     * @param processEngine 流程引擎
     * @return 运行时服务
     */
    public static RuntimeService getRuntimeService(ProcessEngine processEngine) {
        if (processEngine == null) {
            return null;
        }
        return processEngine.getRuntimeService();
    }

    /**
     * 获取任务服务
     *
     * @param processEngine 流程引擎
     * @return 任务服务
     */
    public static TaskService getTaskService(ProcessEngine processEngine) {
        if (processEngine == null) {
            return null;
        }
        return processEngine.getTaskService();
    }

}
