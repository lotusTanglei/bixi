package com.lotus.bserver.service.impl;

import com.lotus.bserver.entity.Test;
import com.lotus.bserver.mapper.TestMapper;
import com.lotus.bserver.service.TestService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 唐磊
 * @since 2022-09-23 22:22:06
 */
@Service
public class TestServiceImpl extends ServiceImpl<TestMapper, Test> implements TestService {

}
