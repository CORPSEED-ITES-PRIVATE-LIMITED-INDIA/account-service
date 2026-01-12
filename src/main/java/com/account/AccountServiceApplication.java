package com.account;

import com.account.domain.PaymentType;
import com.account.repository.PaymentTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

@Slf4j
@SpringBootApplication
@EnableFeignClients
@RequiredArgsConstructor
public class AccountServiceApplication {

	private final PaymentTypeRepository paymentTypeRepository;

	public static void main(String[] args) {
		SpringApplication.run(AccountServiceApplication.class, args);
	}

	/**
	 * Seeds the 4 default Payment Types when the application starts,
	 * only if they do not already exist (checked by 'code').
	 */
	@Bean
	public CommandLineRunner seedPaymentTypes() {
		return args -> {
			log.info("Checking for initial PaymentType data...");

			// Define the 4 required payment types — manually without builder
			List<PaymentType> defaultTypes = Arrays.asList(
					createPaymentType("FULL", "Full Payment",
							"Customer pays the entire amount in one transaction", true),

					createPaymentType("PARTIAL", "Partial Payment",
							"Customer pays in installments without predefined milestones", true),

					createPaymentType("INSTALLMENT", "Installment / Milestone Payment",
							"Payments tied to predefined milestones or operational stages", true),

					createPaymentType("PURCHASE_ORDER", "Purchase Order Payment",
							"Payments against customer-issued Purchase Order – operations may begin early", true)
			);

			int seededCount = 0;

			for (PaymentType type : defaultTypes) {
				if (!paymentTypeRepository.existsByCode(type.getCode())) {
					paymentTypeRepository.save(type);
					seededCount++;
					log.info("Seeded missing PaymentType: {} ({})", type.getCode(), type.getName());
				}
			}

			if (seededCount > 0) {
				log.info("Successfully seeded {} new PaymentType records", seededCount);
			} else {
				log.info("All required PaymentTypes already exist — skipping seed");
			}
		};
	}

	// Helper method — makes the code cleaner and avoids repetition
	private PaymentType createPaymentType(String code, String name, String description, boolean active) {
		PaymentType type = new PaymentType();
		type.setCode(code);
		type.setName(name);
		type.setDescription(description);
		type.setActive(active);
		return type;
	}
}