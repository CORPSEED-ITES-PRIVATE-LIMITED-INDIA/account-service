package com.account.dashboard.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.PaymentRegister;
@Repository
public interface PaymentRegisterRepository  extends JpaRepository<PaymentRegister, Long> {

	@Query(value = "SELECT * FROM payment_register v WHERE v.estimate_id =:id", nativeQuery = true)
	List<PaymentRegister> findAllByEstimateId(long id);
	
	@Query(value = "SELECT * FROM payment_register v WHERE v.estimate_id =:id and v.status =:status", nativeQuery = true)
	List<PaymentRegister> findAllByEstimateId(long id,String status);
	
	
	@Query(value = "SELECT * FROM payment_register v WHERE v.status =:status", nativeQuery = true)
	List<PaymentRegister> findAllByEstimateId(String status);
	
	@Query(value = "SELECT * FROM payment_register v WHERE v.status in(:status)", nativeQuery = true)
	List<PaymentRegister> findAllByStatus(List<String> status);
	@Query(value = "SELECT * FROM payment_register v WHERE v.status in(:status) and pr.payment_date BETWEEN :d1 AND :d2", nativeQuery = true)
	List<PaymentRegister> findAllByStatusInBetweenDate(List<String> status,String d1, String d2);
	
	@Query(value = "SELECT count(*) FROM payment_register v WHERE v.status in(:status) and pr.payment_date BETWEEN :d1 AND :d2", nativeQuery = true)
	long findCountByStatusInBetweenDate(List<String> status,String d1, String d2);

	@Query(value = "SELECT * FROM payment_register v WHERE v.register_type =:registerType", nativeQuery = true)
	List<PaymentRegister> findAllByRegisterType(String registerType);
	
	@Query(value = "SELECT * FROM payment_register v WHERE v.status in(:status)", nativeQuery = true)
	Page<PaymentRegister> findAllByStatus(Pageable pageable,List<String> status);
	
	@Query(value = "SELECT * FROM payment_register v WHERE v.status in(:status) and v.payment_date BETWEEN :d1 AND :d2", nativeQuery = true)
	Page<PaymentRegister> findAllByStatusInBetweenDate(Pageable pageable,List<String> status,String d1,String d2);

	@Query(value = "SELECT * FROM payment_register v WHERE v.name =:name", nativeQuery = true)
	List<PaymentRegister> findByName(String name);
	@Query(value = "SELECT * FROM payment_register v WHERE v.company_name =:name", nativeQuery = true)
	List<PaymentRegister> findBycompanyName(String name);

	@Query(value = "SELECT * FROM payment_register v WHERE v.purchase_date BETWEEN :d1 AND :d2", nativeQuery = true)
	List<PaymentRegister> findByInBetweenDate(String d1,String d2);

    @Query(value = "SELECT pr.id,pr.total_amount,pr.payment_date FROM payment_register pr WHERE pr.payment_date BETWEEN :d1 AND :d2 and pr.created_by_user_id =:userId", nativeQuery = true)
	List<Object[]> findIdAndNameAndCreateDateByInBetweenDateAndAssignee(String d1, String d2, Long userId);

	
    @Query(value = "SELECT pr.id,pr.total_amount,pr.payment_date FROM payment_register pr WHERE pr.payment_date BETWEEN :d1 AND :d2", nativeQuery = true)
	List<Object[]> findIdAndNameAndCreateDateByInBetweenDate(String d1, String d2);
	
	
    @Query(value = "SELECT * FROM payment_register pr WHERE pr.payment_date BETWEEN :d1 AND :d2 and pr.created_by_user_id =:userId", nativeQuery = true)
	List<PaymentRegister> findAllByInBetweenDateAndAssignee(String d1, String d2, Long userId);

    @Query(value = "SELECT * FROM payment_register pr WHERE pr.payment_date BETWEEN :d1 AND :d2", nativeQuery = true)
	List<PaymentRegister> findAllByInBetweenDate(String d1, String d2);
	
    @Query(value = "SELECT * FROM payment_register pr WHERE pr.payment_date BETWEEN :d1 AND :d2 and pr.created_by_user_id =:userId", nativeQuery = true)
	List<PaymentRegister> findIdAllByInBetweenDateAndAssignee(String d1, String d2, Long userId);
   

}
