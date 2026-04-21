package com.rabbittick.streamer.publisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.reactivestreams.Publisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.Metadata;
import com.rabbittick.streamer.global.dto.OrderbookPayload;
import com.rabbittick.streamer.global.dto.TickerPayload;
import com.rabbittick.streamer.metrics.PublishFailureMetrics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.OutboundMessageResult;
import reactor.rabbitmq.Sender;

@ExtendWith(MockitoExtension.class)
class MarketDataPublisherTest {

	@Mock
	private Sender sender;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private PublishFailureMetrics publishFailureMetrics;

	@InjectMocks
	private MarketDataPublisher publisher;

	private MarketDataMessage<TickerPayload> tickerMessage;
	private OutboundMessageResult ackResult;

	private final String exchangeName = "market-data.exchange";

	@BeforeEach
	void setUp() throws Exception {
		ReflectionTestUtils.setField(publisher, "exchangeName", exchangeName);
		tickerMessage = createTickerMessage();
		ackResult = mock(OutboundMessageResult.class);
		lenient().when(ackResult.isAck()).thenReturn(true);
		lenient().when(sender.sendWithPublishConfirms(any())).thenReturn(Flux.just(ackResult));
		lenient().when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
	}

	@Test
	@DisplayName("정상적인 메시지를 올바른 라우팅 키로 발행한다")
	void publishAsync_WithValidMessage_ShouldPublishWithCorrectRoutingKey() throws Exception {
		// when
		publisher.publishAsync(tickerMessage).block();

		// then
		OutboundMessage outbound = captureOutboundMessage();
		assertThat(outbound.getRoutingKey()).isEqualTo("upbit.ticker.KRW-BTC");
		assertThat(outbound.getExchange()).isEqualTo(exchangeName);
	}

	@Test
	@DisplayName("다른 거래소와 마켓코드로 올바른 라우팅 키를 생성한다")
	void publishAsync_WithDifferentExchangeAndMarket_ShouldGenerateCorrectRoutingKey() throws Exception {
		// given
		MarketDataMessage<TickerPayload> binanceMessage = createMessageWithExchangeAndMarket("BINANCE", "BTCUSDT");

		// when
		publisher.publishAsync(binanceMessage).block();

		// then
		OutboundMessage outbound = captureOutboundMessage();
		assertThat(outbound.getRoutingKey()).isEqualTo("binance.ticker.BTCUSDT");
	}

	@Test
	@DisplayName("TRADE 데이터 타입으로 올바른 라우팅 키를 생성한다")
	void publishAsync_WithTradeDataType_ShouldGenerateCorrectRoutingKey() throws Exception {
		// given
		Metadata tradeMetadata = Metadata.builder()
			.messageId("test-id")
			.exchange("UPBIT")
			.dataType("TRADE")
			.collectedAt("2023-01-01T00:00:00.000Z")
			.version("1.0")
			.build();

		TickerPayload payload = TickerPayload.builder()
			.marketCode("KRW-ETH")
			.tradePrice(BigDecimal.valueOf(2000000))
			.build();

		MarketDataMessage<TickerPayload> tradeMessage = MarketDataMessage.<TickerPayload>builder()
			.metadata(tradeMetadata)
			.payload(payload)
			.build();

		// when
		publisher.publishAsync(tradeMessage).block();

		// then
		OutboundMessage outbound = captureOutboundMessage();
		assertThat(outbound.getRoutingKey()).isEqualTo("upbit.trade.KRW-ETH");
	}

	@Test
	@DisplayName("ORDERBOOK 데이터 타입으로 올바른 라우팅 키를 생성한다")
	void publishAsync_WithOrderBookDataType_ShouldGenerateCorrectRoutingKey() throws Exception {
		// given
		Metadata metadata = Metadata.builder()
			.messageId("test-id")
			.exchange("UPBIT")
			.dataType("ORDERBOOK")
			.collectedAt("2023-01-01T00:00:00.000Z")
			.version("1.0")
			.build();

		OrderbookPayload payload = OrderbookPayload.builder()
			.marketCode("KRW-BTC")
			.build();

		MarketDataMessage<OrderbookPayload> orderBookMessage = MarketDataMessage.<OrderbookPayload>builder()
			.metadata(metadata)
			.payload(payload)
			.build();

		// when
		publisher.publishAsync(orderBookMessage).block();

		// then
		OutboundMessage outbound = captureOutboundMessage();
		assertThat(outbound.getRoutingKey()).isEqualTo("upbit.orderbook.KRW-BTC");
	}

	@Test
	@DisplayName("JSON 직렬화 실패 시 Mono가 MessagePublishException으로 실패한다")
	void publishAsync_WhenJsonSerializationFails_ShouldEmitMessagePublishException() throws Exception {
		// given
		JsonProcessingException jsonException = new JsonProcessingException("JSON 변환 실패") {};
		when(objectMapper.writeValueAsBytes(tickerMessage)).thenThrow(jsonException);

		// when & then
		assertThatThrownBy(() -> publisher.publishAsync(tickerMessage).block())
			.isInstanceOf(MarketDataPublisher.MessagePublishException.class)
			.hasMessageContaining("메시지 직렬화 실패")
			.hasCause(jsonException);
	}

	@Test
	@DisplayName("Sender에서 예외 발생 시 MessagePublishException으로 Mono가 실패한다")
	void publishAsync_WhenSenderThrows_ShouldEmitError() throws Exception {
		// given
		when(sender.sendWithPublishConfirms(any()))
			.thenReturn(Flux.error(new RuntimeException("RabbitMQ 연결 실패")));

		// when & then
		assertThatThrownBy(() -> publisher.publishAsync(tickerMessage).block())
			.isInstanceOf(MarketDataPublisher.MessagePublishException.class)
			.hasMessageContaining("메시지 발행 실패")
			.hasCauseInstanceOf(RuntimeException.class);
	}

	@Test
	@DisplayName("마켓코드에 특수문자가 있어도 라우팅 키를 올바르게 생성한다")
	void publishAsync_WithSpecialCharactersInMarketCode_ShouldHandleCorrectly() throws Exception {
		// given
		MarketDataMessage<TickerPayload> message = createMessageWithExchangeAndMarket("UPBIT", "KRW-BTC");

		// when
		publisher.publishAsync(message).block();

		// then
		OutboundMessage outbound = captureOutboundMessage();
		assertThat(outbound.getRoutingKey()).isEqualTo("upbit.ticker.KRW-BTC");
	}

	@Test
	@DisplayName("null 메시지 입력 시 NullPointerException을 발생시킨다")
	void publishAsync_WithNullMessage_ShouldThrowNullPointerException() {
		// when & then
		assertThatThrownBy(() -> publisher.publishAsync(null).block())
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("대소문자 변환이 올바르게 적용된다")
	void publishAsync_ShouldConvertToLowerCase() throws Exception {
		// given
		MarketDataMessage<TickerPayload> message = createMessageWithExchangeAndMarket("UPBIT", "KRW-BTC");

		// when
		publisher.publishAsync(message).block();

		// then
		OutboundMessage outbound = captureOutboundMessage();
		String routingKey = outbound.getRoutingKey();
		assertThat(routingKey).matches("^[a-z]+\\.[a-z]+\\..+$");
		assertThat(routingKey).isEqualTo("upbit.ticker.KRW-BTC");
	}

	@SuppressWarnings("unchecked")
	private OutboundMessage captureOutboundMessage() {
		ArgumentCaptor<Publisher<OutboundMessage>> captor =
			ArgumentCaptor.forClass((Class<Publisher<OutboundMessage>>) (Class<?>) Publisher.class);
		verify(sender).sendWithPublishConfirms(captor.capture());
		return Mono.from(captor.getValue()).block();
	}

	private MarketDataMessage<TickerPayload> createTickerMessage() {
		return createMessageWithExchangeAndMarket("UPBIT", "KRW-BTC");
	}

	private MarketDataMessage<TickerPayload> createMessageWithExchangeAndMarket(String exchange, String marketCode) {
		Metadata metadata = Metadata.builder()
			.messageId("test-message-id")
			.exchange(exchange)
			.dataType("TICKER")
			.collectedAt("2023-01-01T00:00:00.000Z")
			.version("1.0")
			.build();

		TickerPayload payload = TickerPayload.builder()
			.marketCode(marketCode)
			.tradePrice(BigDecimal.valueOf(70000000))
			.tradeVolume(BigDecimal.valueOf(0.1234))
			.timestamp(1672531200000L)
			.build();

		return MarketDataMessage.<TickerPayload>builder()
			.metadata(metadata)
			.payload(payload)
			.build();
	}
}
