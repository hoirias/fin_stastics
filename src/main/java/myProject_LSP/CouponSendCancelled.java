
package myProject_LSP;

public class CouponSendCancelled extends AbstractEvent {

    private Long id;
    private Long orderId;
    private String status;
    private Long sendDate;
    private String couponKind;

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
