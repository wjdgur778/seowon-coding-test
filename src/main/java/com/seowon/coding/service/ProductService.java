package com.seowon.coding.service;

import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.ProductRepository;
import com.seowon.coding.policy.PriceAdjustmentPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    
    private final ProductRepository productRepository;
    
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }
    
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
    
    public Product updateProduct(Long id, Product product) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        product.setId(id);
        return productRepository.save(product);
    }
    
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Product> findProductsByCategory(String category) {
        // TODO #1: 구현 항목
        // Repository를 사용하여 category 로 찾을 제품목록 제공
        List<Product> products = productRepository.findByCategory(category);
        //null check
        if(products.isEmpty())
            throw new RuntimeException("Products not found");
        return products;
    }

//    /**
//     * TODO #6 (리펙토링): 대량 가격 변경 로직을 도메인 객체 안으로 리팩토링하세요.
//     */
//    public void applyBulkPriceChange(List<Long> productIds, double percentage, boolean includeTax) {
//        if (productIds == null || productIds.isEmpty()) {
//            throw new IllegalArgumentException("empty productIds");
//        }
//
//        // 잘못된 구현 예시: double 사용, 루프 내 개별 조회/저장, 하드코딩 세금/반올림 규칙
//        for (Long id : productIds) {
//            Product p = productRepository.findById(id)
//                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));//
//            double base = p.getPrice() == null ? 0.0 : p.getPrice().doubleValue();
//            double changed = base + (base * (percentage / 100.0)); // 부동소수점 오류 가능
//            if (includeTax) {
//                changed = changed * 1.1; // 하드코딩 VAT 10%, 지역/카테고리별 규칙 미반영
//            }
//            // 임의 반올림: 일관되지 않은 스케일/반올림 모드
//            BigDecimal newPrice = BigDecimal.valueOf(changed).setScale(2, RoundingMode.HALF_UP);
//            p.setPrice(newPrice);
//            productRepository.save(p); // 루프마다 저장 (비효율적)
//        }
//    }

    /**
     * TODO #6 (리펙토링): 대량 가격 변경 로직을 도메인 객체 안으로 리팩토링하세요.
     *
     * todo 개선점 및 회고
     *  1. 가격 변경 정책을 따로 분리할 수 있다.
     *      1.1 정책을 record로 분리하여
     *  2. product 안에서 변경 정책을 받아 가격을 변경할 수 있게 한다.
     *  3. product의 price는 @Positive로 양수만 받을 수 있게 되어있다
     *  4. findAllById를 통해 하나의 쿼리로 product를 가져올 수 있게 하되
     *     productIds 와 product리스트의 갯수가 다르면 실패로 판단 (All or Nothing)
     *  5. 현재는 product의 price를 for문을 통해 개별 update 하고 있지만 bulk작업을 위해
     *     productRepository에 직접 update를 하는 메서드를 작성하는 것이 성능상 유리할 것 같다. (jpql)
     *     -> 여기서 고려해야할 점은 아래 두가지의 트레이드 오프이다.
     *                    product(도메인 객체) 안에 로직을 넣는 것(객체 지향 설계)
     *                                        vs
     *                             jpql을 통한 직접적인 update(성능 향상)
     */
    @Transactional
    public void applyBulkPriceChange(List<Long> productIds, double percentage, boolean includeTax) {

        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("empty productIds");
        }
        //
        List<Product> list = productRepository.findAllById(productIds);
        if(list.size()!=productIds.size()) throw new IllegalArgumentException("some Products not found");

        PriceAdjustmentPolicy policy = new PriceAdjustmentPolicy(includeTax,percentage,1.1);

        //개별 업데이트의 문제가 있음
        // bulk update로 변경해야함.
        for(Product p : list){
            p.updatePrice(policy);
        }

        productRepository.saveAll(list);
    }

}
