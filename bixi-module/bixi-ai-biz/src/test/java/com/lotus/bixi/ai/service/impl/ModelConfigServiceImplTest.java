package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.dto.ModelConfigDTO;
import com.lotus.bixi.ai.api.entity.AiModelConfig;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;
import com.lotus.bixi.ai.mapper.AiModelConfigMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ModelConfigServiceImplTest {

    private ModelConfigServiceImpl service;
    private AiModelConfigMapper mapper;
    private Map<Long, AiModelConfig> storedConfigs;

    @BeforeEach
    void setUp() {
        mapper = mock(AiModelConfigMapper.class);
        storedConfigs = new HashMap<>();
        when(mapper.selectByTenantId(anyLong()))
                .thenAnswer(invocation -> storedConfigs.get(invocation.getArgument(0, Long.class)));
        when(mapper.updateValues(anyLong(), nullable(String.class), nullable(Double.class),
                nullable(Integer.class), nullable(Double.class), nullable(String.class), anyLong()))
                .thenAnswer(invocation -> {
                    Long tenantId = invocation.getArgument(0, Long.class);
                    AiModelConfig current = storedConfigs.get(tenantId);
                    if (current == null) {
                        return 0;
                    }
                    String model = invocation.getArgument(1, String.class);
                    Double temperature = invocation.getArgument(2, Double.class);
                    Integer maxTokens = invocation.getArgument(3, Integer.class);
                    Double topP = invocation.getArgument(4, Double.class);
                    String systemPrompt = invocation.getArgument(5, String.class);
                    if (model != null) {
                        current.setCurrentModel(model);
                    }
                    if (temperature != null) {
                        current.setTemperature(temperature);
                    }
                    if (maxTokens != null) {
                        current.setMaxTokens(maxTokens);
                    }
                    if (topP != null) {
                        current.setTopP(topP);
                    }
                    if (systemPrompt != null) {
                        current.setSystemPrompt(systemPrompt);
                    }
                    current.setUpdateBy(invocation.getArgument(6, Long.class));
                    return 1;
                });
        doAnswer(invocation -> {
            AiModelConfig entity = invocation.getArgument(0, AiModelConfig.class);
            storedConfigs.put(entity.getTenantId(), entity);
            return 1;
        }).when(mapper).insert(any(AiModelConfig.class));
        service = new ModelConfigServiceImpl(mapper);
    }

    @AfterEach
    void clearContexts() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void eachTenantStartsWithDefaultsAndKeepsItsOwnUpdates() {
        login(11L, 101L);
        ModelConfigVO initial = service.getConfig();
        assertThat(initial.getCurrentModel()).isEqualTo("qwen-plus");
        assertThat(initial.getTemperature()).isEqualTo(0.7);
        assertThat(initial.getMaxTokens()).isEqualTo(2000);
        assertThat(initial.getTopP()).isEqualTo(0.9);
        assertThat(initial.getSystemPrompt()).isEmpty();

        ModelConfigDTO update = new ModelConfigDTO();
        update.setModel("qwen-max");
        update.setSystemPrompt("tenant 101 prompt");
        service.updateConfig(update);

        ModelConfigVO tenantOne = service.getConfig();
        assertThat(tenantOne.getCurrentModel()).isEqualTo("qwen-max");
        assertThat(tenantOne.getSystemPrompt()).isEqualTo("tenant 101 prompt");
        assertThat(tenantOne.getTemperature()).isEqualTo(0.7);
        assertThat(tenantOne.getMaxTokens()).isEqualTo(2000);
        assertThat(tenantOne.getTopP()).isEqualTo(0.9);

        login(22L, 202L);
        ModelConfigVO tenantTwo = service.getConfig();
        assertThat(tenantTwo.getCurrentModel()).isEqualTo("qwen-plus");
        assertThat(tenantTwo.getTemperature()).isEqualTo(0.7);
        assertThat(tenantTwo.getMaxTokens()).isEqualTo(2000);
        assertThat(tenantTwo.getTopP()).isEqualTo(0.9);
        assertThat(tenantTwo.getSystemPrompt()).isEmpty();

        ModelConfigDTO tenantTwoUpdate = new ModelConfigDTO();
        tenantTwoUpdate.setTemperature(1.2);
        service.updateConfig(tenantTwoUpdate);
        assertThat(service.getConfig().getTemperature()).isEqualTo(1.2);

        login(11L, 101L);
        ModelConfigVO tenantOneAfterSwitch = service.getConfig();
        assertThat(tenantOneAfterSwitch.getCurrentModel()).isEqualTo("qwen-max");
        assertThat(tenantOneAfterSwitch.getSystemPrompt()).isEqualTo("tenant 101 prompt");
        assertThat(tenantOneAfterSwitch.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void aNoOpDatabaseUpdateDoesNotTurnIntoADuplicateInsert() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        AiModelConfig stored = new AiModelConfig();
        stored.setTenantId(101L);
        stored.setDelFlag("0");
        when(mapper.updateValues(anyLong(), nullable(String.class), nullable(Double.class), nullable(Integer.class),
                nullable(Double.class), nullable(String.class), anyLong())).thenReturn(0);
        when(mapper.selectByTenantId(101L)).thenReturn(stored);

        login(11L, 101L);
        ModelConfigDTO update = new ModelConfigDTO();
        update.setModel("qwen-max");

        assertThatCode(() -> new ModelConfigServiceImpl(mapper).updateConfig(update))
                .doesNotThrowAnyException();
        verify(mapper, never()).insert(any(AiModelConfig.class));
    }

    @Test
    void rejectsUnknownModelsAndNonFiniteSamplingValuesBeforePersistence() {
        login(11L, 101L);

        ModelConfigDTO unknownModel = new ModelConfigDTO();
        unknownModel.setModel("provider-private-model");
        assertThatThrownBy(() -> service.updateConfig(unknownModel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的AI模型");

        ModelConfigDTO invalidTemperature = new ModelConfigDTO();
        invalidTemperature.setTemperature(Double.NaN);
        assertThatThrownBy(() -> service.updateConfig(invalidTemperature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
    }

    @Test
    void rejectsAServiceCallWhenTenantContextDoesNotBelongToTheAuthenticatedUser() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        service = new ModelConfigServiceImpl(mapper);
        login(11L, 101L);
        TenantContextHolder.set(202L);

        assertThatThrownBy(service::getConfig)
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("租户上下文");
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsWritesWhileThePlatformIsViewingAllTenants() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        service = new ModelConfigServiceImpl(mapper);
        login(1L, 1L);
        TenantContextHolder.setAllTenantsReadOnly(true);

        ModelConfigDTO update = new ModelConfigDTO();
        update.setModel("qwen-max");

        assertThatThrownBy(() -> service.updateConfig(update))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("all_tenants_read_only");
        verifyNoInteractions(mapper);
    }

    private static void login(long userId, long tenantId) {
        TenantContextHolder.clear();
        TenantContextHolder.set(tenantId);
        var authorities = List.of(new SimpleGrantedAuthority("ai_test"));
        var user = new BixiUser(userId, 1L, tenantId, "user-" + userId, "unused", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }
}
