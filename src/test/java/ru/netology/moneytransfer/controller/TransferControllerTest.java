package ru.netology.moneytransfer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.netology.moneytransfer.exception.InvalidInputException;
import ru.netology.moneytransfer.exception.TransferException;
import ru.netology.moneytransfer.model.ConfirmRequest;
import ru.netology.moneytransfer.model.TransferRequest;
import ru.netology.moneytransfer.service.TransferService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String TRANSFER_JSON = """
            {"cardFromNumber":"1111111111111111","cardFromValidTill":"12/30","cardFromCVV":"111",
             "cardToNumber":"2222222222222222","amount":{"value":10000,"currency":"RUR"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @Test
    void transferReturnsOperationId() throws Exception {
        when(transferService.transfer(any(TransferRequest.class))).thenReturn("op-1");

        mockMvc.perform(post("/transfer").contentType(MediaType.APPLICATION_JSON).content(TRANSFER_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("op-1"));
    }

    @Test
    void transferReturns400WithMessageOnInvalidInput() throws Exception {
        when(transferService.transfer(any(TransferRequest.class)))
                .thenThrow(new InvalidInputException("Карта отправителя не найдена"));

        mockMvc.perform(post("/transfer").contentType(MediaType.APPLICATION_JSON).content(TRANSFER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Карта отправителя не найдена"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void transferReturns400OnMalformedJson() throws Exception {
        mockMvc.perform(post("/transfer").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректный формат запроса"));
    }

    @Test
    void confirmReturnsOperationId() throws Exception {
        when(transferService.confirm(any(ConfirmRequest.class))).thenReturn("op-1");

        mockMvc.perform(post("/confirmOperation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"op-1\",\"code\":\"0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("op-1"));
    }

    @Test
    void confirmReturns500OnTransferError() throws Exception {
        when(transferService.confirm(any(ConfirmRequest.class)))
                .thenThrow(new TransferException("Ошибка перевода: недостаточно средств"));

        mockMvc.perform(post("/confirmOperation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"op-1\",\"code\":\"0000\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ошибка перевода: недостаточно средств"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void unexpectedErrorReturns500() throws Exception {
        when(transferService.confirm(any(ConfirmRequest.class))).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(post("/confirmOperation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"op-1\",\"code\":\"0000\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера"));
    }
}
