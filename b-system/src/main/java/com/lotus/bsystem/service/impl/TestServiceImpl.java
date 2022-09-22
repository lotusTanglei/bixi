package com.lotus.bsystem.service.impl;

import com.lotus.bsystem.entity.Test;
import com.lotus.bsystem.mapper.TestMapper;
import com.lotus.bsystem.service.TestService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 唐磊
 * @since 2022-09-22
 */
@Service
public class TestServiceImpl extends ServiceImpl<TestMapper, Test> implements TestService {

}
