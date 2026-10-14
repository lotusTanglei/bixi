package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.upms.api.service.ClientDetailsQueryService;
import com.lotus.bixi.upms.service.SysOauthClientDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SysClientManagedBrowserTest {

    private final SysOauthClientDetailsService service = mock(SysOauthClientDetailsService.class);
    private MockMvc http;

    @BeforeEach
    void setUp() {
        http = MockMvcBuilders.standaloneSetup(new SysClientController(service, mock(ClientDetailsQueryService.class)))
                .build();
    }

    @Test
    void cannotCreateReservedClientId() throws Exception {
        http.perform(post("/client").contentType("application/json")
                        .content("{\"clientId\":\"bixi-browser\",\"clientSecret\":\"new-secret\",\"scope\":\"server\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(service, never()).saveClient(any());
    }

    @Test
    void cannotCreateClientIdEquivalentUnderDatabaseCollation() throws Exception {
        when(service.list(org.mockito.ArgumentMatchers.<Wrapper<SysOauthClientDetails>>any()))
                .thenReturn(List.of(client(8L, "bixi-browser")));

        http.perform(post("/client").contentType("application/json")
                        .content("{\"clientId\":\"bixí-browser\",\"clientSecret\":\"new-secret\",\"scope\":\"server\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(service, never()).saveClient(any());
    }

    @Test
    void cannotCreateOverReservedClientPrimaryKey() throws Exception {
        when(service.getById(8L)).thenReturn(client(8L, "bixi-browser"));

        http.perform(post("/client").contentType("application/json")
                        .content("{\"id\":8,\"clientId\":\"replacement\",\"clientSecret\":\"new-secret\",\"scope\":\"server\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(service, never()).saveClient(any());
    }

    @Test
    void cannotRenameReservedClientByPrimaryKey() throws Exception {
        when(service.getById(8L)).thenReturn(client(8L, "bixi-browser"));

        http.perform(put("/client").contentType("application/json")
                        .content("{\"id\":8,\"clientId\":\"replacement\",\"scope\":\"server\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(service, never()).updateClientById(any());
    }

    @Test
    void cannotRenameAnotherClientToReservedClientId() throws Exception {
        when(service.getById(9L)).thenReturn(client(9L, "other"));

        http.perform(put("/client").contentType("application/json")
                        .content("{\"id\":9,\"clientId\":\"bixi-browser\",\"scope\":\"server\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(service, never()).updateClientById(any());
    }

    @Test
    void mixedBatchCannotDeleteReservedClientOrOtherClients() throws Exception {
        when(service.listByIds(List.of(8L, 9L)))
                .thenReturn(List.of(client(8L, "bixi-browser"), client(9L, "other")));

        http.perform(delete("/client").contentType("application/json").content("[8,9]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(service, never()).removeBatchByIds(any());
    }

    @Test
    void otherClientsRemainManageable() throws Exception {
        when(service.getById(9L)).thenReturn(client(9L, "other"));
        when(service.listByIds(List.of(9L))).thenReturn(List.of(client(9L, "other")));
        when(service.saveClient(any())).thenReturn(true);
        when(service.updateClientById(any())).thenReturn(true);

        http.perform(post("/client").contentType("application/json")
                        .content("{\"clientId\":\"new-client\",\"clientSecret\":\"new-secret\",\"scope\":\"server\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        http.perform(put("/client").contentType("application/json")
                        .content("{\"id\":9,\"clientId\":\"other\",\"scope\":\"server\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        http.perform(delete("/client").contentType("application/json").content("[9]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(service).saveClient(any());
        verify(service).updateClientById(any());
        verify(service).removeBatchByIds(List.of(9L));
    }

    private static SysOauthClientDetails client(Long id, String clientId) {
        SysOauthClientDetails details = new SysOauthClientDetails();
        details.setId(id);
        details.setClientId(clientId);
        return details;
    }
}
