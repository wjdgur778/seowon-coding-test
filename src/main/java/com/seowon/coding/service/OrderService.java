package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.OrderItem;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }


    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }

    //더티 채킹 및 롤백을 위한 transactional
    @Transactional
    public Order placeOrder(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        // TODO #3: 구현 항목
        // * 주어진 고객 정보로 새 Order를 생성 v
        // * 지정된 Product를 주문에 추가 v
        // * order 의 상태를 PENDING 으로 변경 v
        // * orderDate 를 현재시간으로 설정 v
        // * order 를 저장 v
        // * 각 Product 의 재고를 수정
        // * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.

        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .build();

        for (int i = 0; i < productIds.size(); i++) {
            Long p_id = productIds.get(i);
            Integer p_qu = quantities.get(i);
            Product p = productRepository.findById(p_id)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + p_id));

            if(!p.isInStock()){
                throw new IllegalArgumentException("there is no stock in : " + p_id);
            }
            p.decreaseStock(p_qu);

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(p)
                    .quantity(p_qu)
                    .build();

            order.addItem(item);
        }

        Order result = orderRepository.save(order);

        return result;
    }

    /**
     * TODO #4 (리펙토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 조회는 도메인 객체 밖에서 해결하여 의존 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 사용해도 됩니다.
     */
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {
        if (customerName == null || customerEmail == null) {
            throw new IllegalArgumentException("customer info required");
        }
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }

        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();


        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderProduct req : orderProducts) {
            Long pid = req.getProductId();
            int qty = req.getQuantity();

            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));
            if (qty <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + qty);
            }
            if (product.getStockQuantity() < qty) {
                throw new IllegalStateException("insufficient stock for product " + pid);
            }

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(qty)
                    .price(product.getPrice())
                    .build();
            order.getItems().add(item);

            product.decreaseStock(qty);
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        }

        BigDecimal shipping = subtotal.compareTo(new BigDecimal("100.00")) >= 0 ? BigDecimal.ZERO : new BigDecimal("5.00");
        BigDecimal discount = (couponCode != null && couponCode.startsWith("SALE")) ? new BigDecimal("10.00") : BigDecimal.ZERO;

        order.setTotalAmount(subtotal.add(shipping).subtract(discount));
        order.setStatus(Order.OrderStatus.PROCESSING);
        return orderRepository.save(order);
    }

    /**
     * TODO #5: 코드 리뷰 - 장시간 작업과 진행률 저장의 트랜잭션 분리
     * - 시나리오: 일괄 배송 처리 중 진행률을 저장하여 다른 사용자가 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 예외 전파/롤백 범위, 가독성 등
     * - 상식적인 수준에서 요구사항(기획)을 가정하며 최대한 상세히 작성하세요.
     */
    @Transactional
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));
        ps.markRunning(orderIds == null ? 0 : orderIds.size());
        processingStatusRepository.save(ps);

        int processed = 0;
        /**
         * # 1
         *   아래의 for문의 가독성이 떨어지는 것 같습니다.
         *   orderIds의 null 여부를 먼저 파악한 후에
         *   for(Long orderId : orderIds){} 로 작성하면 보다 가독성이 좋을 것 같습니다.
         */
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) {
            try {
                // 오래 걸리는 작업 이라는 가정 시뮬레이션 (예: 외부 시스템 연동, 대용량 계산 등)
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING));
                // 중간 진행률 저장
                this.updateProgressRequiresNew(jobId, ++processed, orderIds.size());
            } catch (Exception e) {
            }
        }
        ps = processingStatusRepository.findByJobId(jobId).orElse(ps);
        ps.markCompleted();
        processingStatusRepository.save(ps);
    }
    /**
     * # 2
     *  중간 진행률 로직을 "Propagation.REQUIRES_NEW" 를 통해 전파시켰습니다.
     *  이는 만약, 핵심 비즈니스 로직 (배송 처리)에 문제가 생겨 롤백이 되어도 진행률 저장이 될 것입니다.
     *  이 때문에 사용자는 비정상적인 진행률을 보게 될 수도 있습니다.
     *  해당 트랜젝션을 원래의 트랜잭션 범위에 포함시켜 잘못된 진행률이 저장되지 않도록 해야 합니다.
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressRequiresNew(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }

}