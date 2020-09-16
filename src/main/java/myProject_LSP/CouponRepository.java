package myProject_LSP;

import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends PagingAndSortingRepository<Coupon, Long>{
    Optional<Coupon> findByOrderId(Long orderId);


}