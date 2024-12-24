

package com.lotus.bixi.upms.api.feign;


import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.feign.annotation.NoToken;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author 唐磊
 * @date 2018/6/22
 */
@FeignClient(contextId = "remoteUserService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteUserService {

    /**
     * (未登录状态调用，需要加 @NoToken) 通过用户名查询用户、角色信息
     *
     * @param user 用户查询对象
     * @return R
     */
    @NoToken
    @GetMapping("/user/info/query")
    R<UserInfo> info(@SpringQueryMap UserDTO user);

}
