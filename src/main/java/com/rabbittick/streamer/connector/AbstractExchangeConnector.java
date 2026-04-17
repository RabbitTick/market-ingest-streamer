package com.rabbittick.streamer.connector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import com.rabbittick.streamer.service.MarketDataService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * 거래소 WebSocket 커넥터의 공통 생명주기를 구현하는 추상 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>마켓코드 청크 분할 및 병렬 WebSocket 연결 관리</li>
 *   <li>연결 실패 시 지수 백오프 기반 자동 재연결</li>
 *   <li>주기적 Ping 전송으로 연결 유지</li>
 *   <li>메시지 수신 루프 및 {@code boundedElastic} 스케줄러 기반 비동기 처리</li>
 *   <li>애플리케이션 종료 시 연결 정리</li>
 * </ul>
 *
 * <p>거래소별 프로토콜 차이(구독 메시지 포맷, 메시지 파싱, 마켓코드 로드 방식)는
 * {@link ExchangeConnector} 인터페이스의 추상 메서드로 위임한다.
 *
 * <p>새로운 거래소를 추가하려면 이 클래스를 상속하고
 * {@link ExchangeConnector}의 추상 메서드와 {@link #getMaxMarketsPerConnection()},
 * {@link #getProcessingConcurrency()}를 구현한다.
 */
@Slf4j
public abstract class AbstractExchangeConnector implements ExchangeConnector, DisposableBean {

    protected final WebSocketClient webSocketClient;
    protected final MarketDataService marketDataService;

    /** WebSocket 연결 상태를 관리하는 Disposable. 종료 시 연결 정리에 사용된다. */
    private Disposable connectionDisposable;

    /**
     * 공통 의존성을 주입받는 생성자.
     *
     * @param webSocketClient WebSocket 연결 클라이언트
     * @param marketDataService 시장 데이터 처리 서비스
     */
    protected AbstractExchangeConnector(WebSocketClient webSocketClient,
                                        MarketDataService marketDataService) {
        this.webSocketClient = webSocketClient;
        this.marketDataService = marketDataService;
    }

    /**
     * 단일 WebSocket 연결당 최대 구독 마켓코드 수를 반환한다.
     *
     * <p>이 값을 초과하면 마켓코드를 청크로 나눠 복수의 연결을 유지한다.
     * 거래소 API 제약에 맞게 구현체가 직접 정의한다.
     *
     * @return 연결당 최대 마켓코드 수
     */
    protected abstract int getMaxMarketsPerConnection();

    /**
     * 메시지 처리 시 동시에 실행할 내부 Mono의 최대 개수를 반환한다.
     *
     * <p>파싱·변환·발행 작업은 {@code boundedElastic} 스케줄러에서
     * 이 값을 상한으로 병렬 처리된다.
     *
     * @return 처리 동시성 상한
     */
    protected abstract int getProcessingConcurrency();

    /**
     * 마켓코드를 로드하고 WebSocket 연결을 시작한다.
     *
     * <p>{@link #loadMarketCodes()}로 구독 대상을 확정한 후
     * {@link #getMaxMarketsPerConnection()} 단위로 청크를 나눠
     * 병렬 연결을 시작한다.
     */
    @Override
    public void start() {
        List<String> marketCodes = loadMarketCodes();
        int chunkCount = (int) Math.ceil((double) marketCodes.size() / getMaxMarketsPerConnection());
        log.info("[{}] {}개 마켓코드 → {}개 WebSocket 연결을 시작합니다.",
            getExchangeName(), marketCodes.size(), chunkCount);
        connectAndSubscribe(marketCodes);
    }

    /**
     * 마켓코드를 청크로 분할하여 병렬 WebSocket 연결을 시작한다.
     *
     * <p>연결 실패 시 초기 5초, 최대 1분의 지수 백오프로 무제한 재연결을 시도한다.
     *
     * @param marketCodes 구독할 전체 마켓코드 목록
     */
    private void connectAndSubscribe(List<String> marketCodes) {
        List<List<String>> chunks = partition(marketCodes, getMaxMarketsPerConnection());
        log.info("[{}] 총 {}개 청크로 병렬 연결 시작", getExchangeName(), chunks.size());

        this.connectionDisposable = Flux.fromIterable(chunks)
            .flatMap(chunk -> webSocketClient
                .execute(getWebSocketUri(), session -> handleWebSocketSession(session, chunk))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
                    .maxBackoff(Duration.ofMinutes(1))
                    .doBeforeRetry(retrySignal ->
                        log.warn("[{}] WebSocket 연결 재시도 중 (마켓코드 {}개, 시도: {})",
                            getExchangeName(), chunk.size(), retrySignal.totalRetries() + 1)))
                .doOnError(error ->
                    log.error("[{}] WebSocket 연결 복구 불가능한 오류 (마켓코드 {}개)",
                        getExchangeName(), chunk.size(), error)))
            .subscribe(
                null,
                error -> log.error("[{}] 전체 WebSocket 연결에 복구 불가능한 오류 발생", getExchangeName(), error),
                () -> log.info("[{}] 전체 WebSocket 연결이 정상적으로 종료되었습니다.", getExchangeName())
            );
    }

    /**
     * 리스트를 {@code chunkSize} 단위로 분할하여 반환한다.
     *
     * @param list 분할할 원본 리스트
     * @param chunkSize 청크 크기
     * @return 분할된 청크 리스트
     */
    private <T> List<List<T>> partition(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

    /**
     * WebSocket 세션을 처리한다.
     * 구독 메시지 전송, 주기적 Ping, 메시지 수신을 병렬로 실행한다.
     *
     * @param session WebSocket 세션
     * @param marketCodes 구독할 마켓코드 목록
     * @return 세션 처리 완료를 나타내는 Mono
     */
    private Mono<Void> handleWebSocketSession(WebSocketSession session, List<String> marketCodes) {
        log.info("[{}] WebSocket 연결 성공 (마켓코드 {}개), 데이터 구독을 시작합니다.",
            getExchangeName(), marketCodes.size());

        Mono<Void> subscription = sendSubscription(session, marketCodes);
        Mono<Void> keepAlive = sendPeriodicPing(session);
        Mono<Void> receiveMessages = receiveAndProcessMessages(session);

        return Mono.when(subscription, keepAlive, receiveMessages);
    }

    /**
     * {@link #getPingInterval()} 주기로 Ping 메시지를 전송하여 연결을 유지한다.
     *
     * @param session WebSocket 세션
     * @return Ping 전송 완료를 나타내는 Mono
     */
    private Mono<Void> sendPeriodicPing(WebSocketSession session) {
        return Flux.interval(getPingInterval())
            .doOnNext(tick -> log.trace("[{}] Ping 메시지 전송 (tick: {})", getExchangeName(), tick))
            .flatMap(tick -> {
                WebSocketMessage pingMessage = session.pingMessage(factory ->
                    factory.wrap("ping".getBytes()));
                return session.send(Mono.just(pingMessage));
            })
            .onErrorContinue((error, obj) ->
                log.warn("[{}] Ping 전송 실패, 연결 상태를 확인하세요: {}", getExchangeName(), error.getMessage()))
            .then();
    }

    /**
     * WebSocket으로부터 메시지를 수신하고 {@link #handleMessage(String)}에 위임한다.
     *
     * <p>블로킹 작업(파싱·변환·발행)은 {@code boundedElastic} 스케줄러에서 실행되어
     * 수신 스레드가 다음 메시지를 계속 받을 수 있도록 한다.
     *
     * @param session WebSocket 세션
     * @return 메시지 수신 완료를 나타내는 Mono
     */
    private Mono<Void> receiveAndProcessMessages(WebSocketSession session) {
        return session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .flatMap(rawJson ->
                handleMessage(rawJson)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(error -> {
                        log.warn("[{}] 메시지 처리 실패, 다음 메시지 계속 처리: {}",
                            getExchangeName(), error.getMessage());
                        return Mono.empty();
                    }),
                getProcessingConcurrency())
            .then();
    }

    /**
     * 애플리케이션 종료 시 WebSocket 연결을 정리한다.
     *
     * <p>{@link DisposableBean} 구현을 통해 Spring 컨테이너 종료 시 자동으로 호출된다.
     */
    @Override
    public void destroy() {
        if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
            log.info("[{}] WebSocket 연결을 종료합니다.", getExchangeName());
            connectionDisposable.dispose();
        }
    }
}
