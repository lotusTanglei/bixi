package com.lotus.bixi.common.core.constant;

/**
 * 公共常量
 * @author bixi
 * @date 2025-01-01
 */
public interface CommonConstants {

    /**
     * 前缀
     */
    String PROJECT_PREFIX = "bixi";


    /**
     * 删除
     */
    String STATUS_DEL = "1";

    /**
     * 正常
     */
    String STATUS_NORMAL = "0";

    /**
     * 锁定
     */
    String STATUS_LOCK = "9";

    /**
     * 菜单树根节点
     */
    Long MENU_TREE_ROOT_ID = -1L;

    /**
     * 菜单
     */
    String MENU = "0";

    /**
     * 编码
     */
    String UTF8 = "UTF-8";

    /**
     * JSON 资源
     */
    String CONTENT_TYPE = "application/json; charset=utf-8";

    /**
     * 前端工程名
     */
    String FRONT_END_PROJECT = "bixi-ui";

    /**
     * 后端工程名
     */
    String BACK_END_PROJECT = "bixi";

    /**
     * 成功标记
     */
    Integer SUCCESS = 0;

    /**
     * 失败标记
     */
    Integer FAIL = 1;

    /**
     * 当前页
     */
    String CURRENT = "current";

    /**
     * size
     */
    String SIZE = "size";

    /**
     * 请求开始时间
     */
    String REQUEST_START_TIME = "REQUEST-START-TIME";

}
