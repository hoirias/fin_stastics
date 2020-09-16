package myProject_LSP;

import myProject_LSP.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @Autowired
    CouponRepository couponRepository;
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrderCancelled_CouponSendCancel(@Payload OrderCancelled orderCancelled){
    //수정
        if(orderCancelled.isMe()){

            System.out.println("##### listener CouponCancelUpdate : " + orderCancelled.toJson());
            Optional<Coupon> couponOptional = couponRepository.findByOrderId(orderCancelled.getId());
            Coupon coupon = couponOptional.get();
            if("ORDER : ORDER CANCELED".equals(orderCancelled.getStatus())){
                coupon.setStatus("COUPON : COUPON SEND CANCELLED BY ORDER CANCEL");

            }



            couponRepository.save(coupon);

        }
    }

}
