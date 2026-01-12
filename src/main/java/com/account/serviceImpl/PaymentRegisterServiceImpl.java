package com.account.serviceImpl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.account.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.account.config.GetAllCompanyDto;
import com.account.config.LeadFeignClient;
import com.account.config.OpenAPIConfig;
import com.account.domain.FileData;
import com.account.domain.InvoiceData;
import com.account.domain.Organization;
import com.account.domain.PaymentRegister;
import com.account.domain.Unbilled;
import com.account.domain.User;
import com.account.domain.account.Ledger;
import com.account.domain.account.LedgerType;
import com.account.domain.account.Voucher;
import com.account.domain.account.VoucherType;
import com.account.dto.AddGstDto;
import com.account.dto.CreateAmountDto;
import com.account.dto.CreatePurchaseOrderDto;
import com.account.dto.CreateTdsDto;
import com.account.dto.PaymentApproveDto;
import com.account.dto.UpdatePaymentDto;
import com.account.service.PaymentRegisterService;

import jakarta.transaction.Transactional;


@Service
public class PaymentRegisterServiceImpl implements  PaymentRegisterService {

	@Override
	public PaymentRegister createPaymentRegister(CreateAmountDto createAmountDto) throws Exception {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllPaymentRegister(String status) {
		return null;
	}

	@Override
	public Boolean updatePaymentRegister(UpdatePaymentDto updatePaymentDto) {
		return null;
	}

	@Override
	public Boolean paymentApproveAndDisapproved(UpdatePaymentDto updatePaymentDto) {
		return null;
	}

	@Override
	public PaymentRegister getPaymentRegisterById(long id) {
		return null;
	}

	@Override
	public Boolean paymentApproveAndDisapprovedV3(Long paymentRegisterId, Long estimateId) {
		return null;
	}

	@Override
	public List<PaymentRegister> getPaymentRegisterByEstimateId(long id) {
		return null;
	}

	@Override
	public Boolean createInvoice(Long id) {
		return null;
	}

	@Override
	public Map<String, Object> getInvoice(Long id) {
		return null;
	}

	@Override
	public Map<String, Integer> leftAmount(Long id) {
		return null;
	}

	@Override
	public List<PaymentRegister> getAllPaymentRegisterByStatus(String status) {
		return null;
	}

	@Override
	public Boolean paymentDisapproved(Long id) {
		return null;
	}

	@Override
	public Boolean allPaymentApprovedV3(Long paymentRegisterId) {
		return null;
	}

	@Override
	public Boolean paymentApproveAndDisapprovedV4(Long paymentRegisterId, Long estimateId) {
		return null;
	}

	@Override
	public List<InvoiceData> getAllInvoiceAccordingToUser(Long userId) {
		return null;
	}

	@Override
	public List<InvoiceData> getAllInvoiceForSales(Long userId) {
		return null;
	}

	@Override
	public PaymentRegister createPurchaseOrder(CreatePurchaseOrderDto createPurchaseOrderDto) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllPurchaseOrder(Long userId) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllPaymentRegisterWithCompany(Long userId) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllPaymentRegisterWithPage(int page, int size, String status) {
		return null;
	}

	@Override
	public Long getAllPaymentRegisterCount(String status) {
		return null;
	}

	@Override
	public List<PaymentRegister> getAllPaymentRegisterByUser(int page, int size, Long userId, String status) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllInvoice(Long userId, int page, int size) {
		return null;
	}

	@Override
	public long getAllInvoiceCount(Long userId) {
		return 0;
	}

	@Override
	public Map<String, Object> getRemainingAmount(Long id) {
		return null;
	}

	@Override
	public List<Map<String, Object>> searchPaymentRegister(String searchParam, String name, String fromDate, String toDate) {
		return null;
	}

	@Override
	public Map<String, Object> paymentApproveAndDisapprovedManual(PaymentApproveDto paymentApproveDto) {
		return null;
	}

	@Override
	public Map<String, Object> getPaymentRegisterWithEstimateById(Long id) {
		return null;
	}
}

