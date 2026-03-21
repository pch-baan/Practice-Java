package concurrency.bloom.l3.IdGeneration.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository repo;

    @Transactional
    public Order placeOrder(String customerId) {
        Order order = Order.builder()
                .customerId(customerId)
                .createdAt(LocalDateTime.now())
                .build();
        Order saved = repo.save(order);
        log.info("[PRODUCTION] Order #{} placed for customer {}", saved.getId(), customerId);
        return saved;
    }

    public List<Order> findAll() {
        return repo.findAll();
    }
}
