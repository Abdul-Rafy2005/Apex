package com.abdulrafy.backend.trading.event;

import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TradeExecutedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutedConsumer.class);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBroadcaster portfolioBroadcaster;

    public TradeExecutedConsumer(PortfolioRepository portfolioRepository,
                                 PortfolioBroadcaster portfolioBroadcaster) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBroadcaster = portfolioBroadcaster;
    }

    @RabbitListener(queues = "trading.trade-executed")
    public void onTradeExecuted(TradeExecutedEvent event) {
        log.info("Received TradeExecuted event: tradeId={}, side={}, quantity={}, price={}",
                event.tradeId(), event.side(), event.quantity(), event.price());

        portfolioRepository.findUserIdByPortfolioId(event.portfolioId())
                .ifPresent(userId -> portfolioBroadcaster.broadcastPortfolioUpdate(userId, event));
    }
}
