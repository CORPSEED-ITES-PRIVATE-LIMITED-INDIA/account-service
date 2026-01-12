package com.account.serviceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.account.dto.UnbilledDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.account.config.LeadFeignClient;
import com.account.domain.Unbilled;
import com.account.repository.UnbilledRepository;
import com.account.service.UnbilledService;
@Service
public class UnbilledServiceImpl implements UnbilledService{



}
