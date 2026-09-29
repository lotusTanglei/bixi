package com.lotus.bixi.quartz.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.service.SysJobRecordService;
import com.lotus.bixi.quartz.service.SysJobService;
import com.lotus.bixi.quartz.util.TaskUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SysJobControllerFilterTest {

	@Test
	@SuppressWarnings({"rawtypes", "unchecked"})
	void statusFilterUsesStatusInsteadOfGroup() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "quartz-filter-test"),
			SysJob.class);
		SysJobService service = mock(SysJobService.class);
		SysJobController controller = new SysJobController(service, mock(SysJobRecordService.class),
			new TaskUtil(), null);
		SysJob query = new SysJob();
		query.setGroup("group-a");
		query.setStatus("2");

		controller.getSysJobPage(new Page<>(), query);

		ArgumentCaptor<LambdaQueryWrapper> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
		verify(service).page(any(Page.class), wrapper.capture());
		wrapper.getValue().getSqlSegment();
		assertThat(wrapper.getValue().getParamNameValuePairs().values())
			.contains("%group-a%", "2");
	}
}
