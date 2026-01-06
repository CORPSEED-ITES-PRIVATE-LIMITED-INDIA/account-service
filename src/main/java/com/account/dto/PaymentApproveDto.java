package com.account.dto;

import org.springframework.web.bind.annotation.RequestParam;

public class PaymentApproveDto {
	
	Long paymentRegisterId ;

    Long estimateId;
    String comment;
    String status;
	
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public Long getPaymentRegisterId() {
		return paymentRegisterId;
	}
	public void setPaymentRegisterId(Long paymentRegisterId) {
		this.paymentRegisterId = paymentRegisterId;
	}
	public Long getEstimateId() {
		return estimateId;
	}
	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}
    
    
    

}
