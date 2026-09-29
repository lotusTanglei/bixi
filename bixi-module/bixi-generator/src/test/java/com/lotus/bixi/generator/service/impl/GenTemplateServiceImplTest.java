package com.lotus.bixi.generator.service.impl;

import com.lotus.bixi.common.core.exception.CheckedException;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.dto.TemplateUpdateResult;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.entity.GenTemplate;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import com.lotus.bixi.generator.mapper.GenGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateMapper;
import com.lotus.bixi.generator.template.remote.TemplateUpdateManifest;
import com.lotus.bixi.generator.template.remote.TemplateUpdatePackage;
import com.lotus.bixi.generator.template.remote.TemplateUpdatePackageLoader;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenTemplateServiceImplTest {

	private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
	private static final String DIGEST = "a".repeat(64);

	@Test
	void disabledRemoteUpdatesNeverInspectOrPersist() {
		Fixture fixture = fixture(false);

		assertThat(fixture.service.checkVersion().getData()).isEqualTo(true);
		assertThatThrownBy(fixture.service::onlineUpdate)
				.isInstanceOf(CheckedException.class)
				.hasMessageContaining("未启用");

		verifyNoInteractions(fixture.loader, fixture.groupMapper, fixture.templateMapper,
				fixture.templateGroupMapper);
	}

	@Test
	void installsOneFullyVerifiedPackageAndReturnsItsSourceIdentity() {
		Fixture fixture = fixture(true);
		TemplateUpdatePackageLoader.VerifiedManifest manifest = manifest();
		TemplateUpdatePackage updatePackage = updatePackage();
		when(fixture.loader.inspect()).thenReturn(manifest);
		when(fixture.loader.load(manifest)).thenReturn(updatePackage);
		when(fixture.groupMapper.exists(any())).thenReturn(false);
		doAnswer(invocation -> {
			invocation.<GenGroup>getArgument(0).setId(41L);
			return 1;
		}).when(fixture.groupMapper).insert(any(GenGroup.class));
		AtomicLong templateId = new AtomicLong(100L);
		doAnswer(invocation -> {
			invocation.<GenTemplate>getArgument(0).setId(templateId.incrementAndGet());
			return 1;
		}).when(fixture.templateMapper).insert(any(GenTemplate.class));
		when(fixture.templateGroupMapper.insert(any(GenTemplateGroup.class))).thenReturn(1);

		R<TemplateUpdateResult> response = fixture.service.onlineUpdate();

		assertThat(response.getData()).isEqualTo(new TemplateUpdateResult(
				true, REVISION, DIGEST, "bixi-default@" + REVISION, 2));
		var group = org.mockito.ArgumentCaptor.forClass(GenGroup.class);
		verify(fixture.groupMapper).insert(group.capture());
		assertThat(group.getValue().getGroupName()).isEqualTo("bixi-default@" + REVISION);
		assertThat(group.getValue().getGroupDesc()).contains(REVISION, DIGEST);
		var templates = org.mockito.ArgumentCaptor.forClass(GenTemplate.class);
		verify(fixture.templateMapper, org.mockito.Mockito.times(2)).insert(templates.capture());
		assertThat(templates.getAllValues()).extracting(GenTemplate::getGeneratorPath)
				.containsExactly("entity.java", "mapper.java");
		var relations = org.mockito.ArgumentCaptor.forClass(GenTemplateGroup.class);
		verify(fixture.templateGroupMapper, org.mockito.Mockito.times(2)).insert(relations.capture());
		assertThat(relations.getAllValues()).extracting(GenTemplateGroup::getGroupId)
				.containsOnly(41L);
	}

	@Test
	void existingRevisionReturnsStableMetadataWithoutDownloadingFilesOrWriting() {
		Fixture fixture = fixture(true);
		TemplateUpdatePackageLoader.VerifiedManifest manifest = manifest();
		when(fixture.loader.inspect()).thenReturn(manifest);
		when(fixture.groupMapper.exists(any())).thenReturn(true);

		R<TemplateUpdateResult> response = fixture.service.onlineUpdate();

		assertThat(response.getData()).isEqualTo(new TemplateUpdateResult(
				false, REVISION, DIGEST, "bixi-default@" + REVISION, 2));
		verify(fixture.loader, never()).load(any());
		verify(fixture.groupMapper, never()).insert(any(GenGroup.class));
		verifyNoInteractions(fixture.templateMapper, fixture.templateGroupMapper);
	}

	@Test
	void packageFailureOccursBeforeAnyDatabaseMutationAndWriteMethodIsTransactional() throws Exception {
		Fixture fixture = fixture(true);
		TemplateUpdatePackageLoader.VerifiedManifest manifest = manifest();
		when(fixture.loader.inspect()).thenReturn(manifest);
		when(fixture.groupMapper.exists(any())).thenReturn(false);
		when(fixture.loader.load(manifest)).thenThrow(new IllegalArgumentException("SHA-256不匹配"));

		assertThatThrownBy(fixture.service::onlineUpdate)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SHA-256");
		verify(fixture.groupMapper, never()).insert(any(GenGroup.class));
		verifyNoInteractions(fixture.templateMapper, fixture.templateGroupMapper);

		Transactional transaction = GenTemplateServiceImpl.class.getMethod("onlineUpdate")
				.getAnnotation(Transactional.class);
		assertThat(transaction).isNotNull();
		assertThat(transaction.rollbackFor()).contains(Exception.class);
	}

	@Test
	void concurrentInstallationOfTheSameRevisionReturnsTheStableExistingResult() {
		Fixture fixture = fixture(true);
		TemplateUpdatePackageLoader.VerifiedManifest manifest = manifest();
		when(fixture.loader.inspect()).thenReturn(manifest);
		when(fixture.loader.load(manifest)).thenReturn(updatePackage());
		when(fixture.groupMapper.exists(any())).thenReturn(false);
		when(fixture.groupMapper.selectCount(any())).thenReturn(1L);
		when(fixture.groupMapper.insert(any(GenGroup.class)))
				.thenThrow(new DuplicateKeyException("uk_gen_group_active_name"));

		R<TemplateUpdateResult> response = fixture.service.onlineUpdate();

		assertThat(response.getData()).isEqualTo(new TemplateUpdateResult(
				false, REVISION, DIGEST, "bixi-default@" + REVISION, 2));
		verifyNoInteractions(fixture.templateMapper, fixture.templateGroupMapper);
	}

	private static Fixture fixture(boolean enabled) {
		GenTemplateGroupMapper relationMapper = mock(GenTemplateGroupMapper.class);
		GenGroupMapper groupMapper = mock(GenGroupMapper.class);
		GenTemplateMapper templateMapper = mock(GenTemplateMapper.class);
		TemplateUpdatePackageLoader loader = mock(TemplateUpdatePackageLoader.class);
		BixiGeneratorDefaultProperties properties = new BixiGeneratorDefaultProperties();
		properties.setAutoCheckVersion(enabled);
		GenTemplateServiceImpl service = new GenTemplateServiceImpl(
				relationMapper, groupMapper, properties, loader);
		ReflectionTestUtils.setField(service, "baseMapper", templateMapper);
		return new Fixture(service, loader, groupMapper, templateMapper, relationMapper);
	}

	private static TemplateUpdatePackageLoader.VerifiedManifest manifest() {
		TemplateUpdateManifest value = new TemplateUpdateManifest(1, REVISION, "bixi-default", List.of(
				new TemplateUpdateManifest.FileEntry("entity", "templates/entity.vm", "entity.java", "b".repeat(64), 6),
				new TemplateUpdateManifest.FileEntry("mapper", "templates/mapper.vm", "mapper.java", "c".repeat(64), 6)));
		return new TemplateUpdatePackageLoader.VerifiedManifest(
				URI.create("https://templates.example/bixi/" + REVISION + "/"), DIGEST, value);
	}

	private static TemplateUpdatePackage updatePackage() {
		return new TemplateUpdatePackage(REVISION, DIGEST, "bixi-default", List.of(
				new TemplateUpdatePackage.TemplateFile("entity", "templates/entity.vm", "entity.java",
						"b".repeat(64), "entity"),
				new TemplateUpdatePackage.TemplateFile("mapper", "templates/mapper.vm", "mapper.java",
						"c".repeat(64), "mapper")));
	}

	private record Fixture(GenTemplateServiceImpl service, TemplateUpdatePackageLoader loader,
			GenGroupMapper groupMapper, GenTemplateMapper templateMapper,
			GenTemplateGroupMapper templateGroupMapper) {
	}
}
