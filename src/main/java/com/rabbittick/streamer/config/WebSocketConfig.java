package com.rabbittick.streamer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

/**
 * WebSocket 클라이언트 설정을 관리하는 Configuration 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Upbit WebSocket API 연결 최적화</li>
 *   <li>연결 타임아웃 및 Keep-alive 설정</li>
 *   <li>네트워크 레벨 성능 튜닝</li>
 *   <li>실시간 데이터 수신 안정성 보장</li>
 * </ul>
 */
@Slf4j
@Configuration
public class WebSocketConfig {

	private static final int CONNECT_TIMEOUT_MILLIS = 10_000;    // 10초
	private static final int READ_TIMEOUT_SECONDS = 70;          // 70초 (Ping 60초 + 여유 10초)
	private static final int WRITE_TIMEOUT_SECONDS = 30;         // 30초
	private static final int MAX_FRAME_PAYLOAD_LENGTH = 1024 * 1024; // 1MB

	/**
	 * Upbit WebSocket 연결에 최적화된 WebSocketClient를 생성한다.
	 *
	 * <p>연결 안정성과 성능을 위해 다음 설정을 적용한다.
	 * <ul>
	 *   <li>연결 타임아웃: 10초</li>
	 *   <li>읽기 타임아웃: 70초 (Upbit Ping/Pong 주기 고려)</li>
	 *   <li>쓰기 타임아웃: 30초</li>
	 *   <li>TCP Keep-alive 활성화</li>
	 *   <li>TCP_NODELAY 활성화 (지연 시간 최소화)</li>
	 *   <li>최대 프레임 크기: 1MB</li>
	 * </ul>
	 *
	 * @return 설정이 적용된 WebSocketClient 인스턴스
	 */
	@Bean
	public WebSocketClient webSocketClient() {
		HttpClient httpClient = HttpClient.create()
			// 연결 타임아웃 설정
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
			// TCP Keep-alive 설정 (장시간 연결 유지)
			.option(ChannelOption.SO_KEEPALIVE, true)
			// TCP_NODELAY 설정 (지연 시간 최소화)
			.option(ChannelOption.TCP_NODELAY, true)
			// 연결/해제 이벤트 로깅
			.doOnConnected(conn -> {
				log.info("WebSocket 클라이언트 연결 설정 완료");
				conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS))
					.addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS));
			})
			.doOnDisconnected(conn ->
				log.info("WebSocket 클라이언트 연결 해제됨"));

		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient(httpClient);

		// 최대 프레임 크기 설정
		client.setMaxFramePayloadLength(MAX_FRAME_PAYLOAD_LENGTH);

		log.info("WebSocketClient 설정 완료 - 연결 타임아웃: {}ms, 읽기 타임아웃: {}초",
			CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_SECONDS);

		return client;
	}
}