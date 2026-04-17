package com.rabbittick.streamer.connector;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 등록된 모든 {@link ExchangeConnector}의 시작과 종료를 조율하는 컴포넌트.
 *
 * <p>Spring이 {@code List<ExchangeConnector>}를 자동 주입하므로
 * 새로운 거래소 커넥터를 추가하면 이 클래스의 수정 없이 자동으로 관리 대상에 포함된다.
 */
@Slf4j
@Component
public class ConnectorManager {

    private final List<ExchangeConnector> connectors;

    public ConnectorManager(List<ExchangeConnector> connectors) {
        this.connectors = connectors;
    }

    /**
     * Spring 애플리케이션이 완전히 준비된 후 활성화된 모든 커넥터를 시작한다.
     *
     * @param event Spring 애플리케이션 준비 완료 이벤트
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startAll(ApplicationReadyEvent event) {
        log.info("ConnectorManager: 등록된 커넥터 {}개 중 활성화된 커넥터를 시작합니다.", connectors.size());
        connectors.stream()
            .filter(ExchangeConnector::isEnabled)
            .forEach(connector -> {
                log.info("[{}] 커넥터 시작", connector.getExchangeName());
                connector.start();
            });
    }
}
