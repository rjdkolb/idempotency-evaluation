package io.r3k.idempotency.evaluate.arun0009lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.r3k.idempotency.evaluate.arun0009lib.order.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class ApplicationTests {

  private final WebApplicationContext webApplicationContext;
  private final ObjectMapper objectMapper;
  private MockMvc mockMvc;

  @Autowired
  ApplicationTests(WebApplicationContext webApplicationContext, ObjectMapper objectMapper) {
    this.webApplicationContext = webApplicationContext;
    this.objectMapper = objectMapper;
  }

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  @DisplayName("posting an order saves it and returns a supplier order reference")
  void postingAnOrderSavesItAndReturnsASupplierOrderReference() throws Exception {
    String requestBody = "{\"customerOrderReference\":\"customer-123\"}";

    String firstResponse =
        mockMvc
            .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerOrderReference").value("customer-123"))
            .andExpect(jsonPath("$.supplierOrderReference").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OrderResponse order = objectMapper.readValue(firstResponse, OrderResponse.class);
    assertThat(order.supplierOrderReference()).startsWith("SUP-");
  }

  @Test
  @DisplayName("posting a duplicate customer order reference returns 409 conflict")
  void postingADuplicateCustomerOrderReferenceReturns409Conflict() throws Exception {
    String requestBody = "{\"customerOrderReference\":\"customer-duplicate\"}";

    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Order Already Exists"))
        .andExpect(jsonPath("$.customerOrderReference").value("customer-duplicate"));
  }

  @Test
  @DisplayName("posting a blank customer order reference returns bad request")
  void postingABlankCustomerOrderReferenceReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerOrderReference\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("posting to /orders/idempotent without an Idempotency-Key returns 400")
  void postingToIdempotentWithoutHeaderReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/orders/idempotent")
                .param("delayMillis", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerOrderReference\":\"customer-no-header\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("posting to /orders/idempotent with a blank Idempotency-Key returns 400")
  void postingToIdempotentWithBlankHeaderReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/orders/idempotent")
                .param("delayMillis", "0")
                .header("Idempotency-Key", "   ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerOrderReference\":\"customer-blank-header\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "two posts to /orders/idempotent with the same Idempotency-Key replay the original response")
  void duplicateIdempotencyKeyReplaysOriginalResponse() throws Exception {
    String requestBody = "{\"customerOrderReference\":\"customer-replay\"}";
    String key = "key-replay-1";

    String firstResponse =
        mockMvc
            .perform(
                post("/orders/idempotent")
                    .param("delayMillis", "0")
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerOrderReference").value("customer-replay"))
            .andExpect(jsonPath("$.supplierOrderReference").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String secondResponse =
        mockMvc
            .perform(
                post("/orders/idempotent")
                    .param("delayMillis", "0")
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OrderResponse first = objectMapper.readValue(firstResponse, OrderResponse.class);
    OrderResponse second = objectMapper.readValue(secondResponse, OrderResponse.class);
    assertThat(second.supplierOrderReference()).isEqualTo(first.supplierOrderReference());

    String listResponse =
        mockMvc
            .perform(get("/orders"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    OrderResponse[] orders = objectMapper.readValue(listResponse, OrderResponse[].class);
    long matches =
        java.util.Arrays.stream(orders)
            .filter(o -> "customer-replay".equals(o.customerOrderReference()))
            .count();
    assertThat(matches).isEqualTo(1);
  }
}
