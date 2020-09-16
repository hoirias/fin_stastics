package myProject_LSP;

import org.springframework.beans.BeanUtils;

import javax.persistence.*;

@Entity
@Table(name="Coupon_table")
public class Coupon {

    //수정
    private static int couponQty=10;

    private boolean couponFlowChk=true;
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private String status;
    private Long sendDate;
    private String couponKind;


    @PostPersist
    public void onPostPersist(){
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ 26 ");
        System.out.println(this.getStatus());
        if("COUPON : COUPON SENDED".equals(this.getStatus())){
            //ORDER -> COUPON SEND 경우
            CouponSended couponSended = new CouponSended();
            BeanUtils.copyProperties(this, couponSended);
            couponSended.publishAfterCommit();
        }
    }

    @PrePersist
    public void onPrePersist(){

        System.out.println(this.getStatus() + "++++++++++++++++++++++++++++++++");

        if("ORDER : COUPON SEND".equals(this.getStatus())){
            this.setCouponKind("10 Percent Sale");
            this.setStatus("COUPON : COUPON SENDED");
            this.setSendDate(System.currentTimeMillis());
        }else {
            this.setStatus("COUPON : COUPON SEND CANCELLED");
            //ORDER -> COUPON CANCEL 경우

            System.out.println("47 *****************************************");
            CouponSendCancelled couponSendCancelled = new CouponSendCancelled();
            BeanUtils.copyProperties(this, couponSendCancelled);
            couponSendCancelled.publishAfterCommit();
        }
    }


    @PreUpdate
    public void onPreUpdate(){
        if("ORDER : COUPON SEND".equals(this.getStatus())){
            this.setCouponKind("10 Percent Sale");
            this.setStatus("COUPON : COUPON SENDED");
            this.setSendDate(System.currentTimeMillis());
        }else {
            this.setStatus("COUPON : COUPON SEND CANCELLED");
            //ORDER -> COUPON CANCEL 경우

            CouponSendCancelled couponSendCancelled = new CouponSendCancelled();
            BeanUtils.copyProperties(this, couponSendCancelled);
            couponSendCancelled.publishAfterCommit();
        }

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public Long getSendDate() {
        return sendDate;
    }

    public void setSendDate(Long sendDate) {
        this.sendDate = sendDate;
    }
    public String getCouponKind() {
        return couponKind;
    }

    public void setCouponKind(String couponKind) {
        this.couponKind = couponKind;
    }




}
