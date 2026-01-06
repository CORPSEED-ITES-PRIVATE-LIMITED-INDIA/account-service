package com.account.controller.account;

import com.account.dto.UnbilledDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.account.service.UnbilledService;
import com.account.util.UrlsMapping;

import java.util.List;
import java.util.Map;

@RestController
public class UnbilledController {

    @Autowired
    private UnbilledService unbilledService;

//    @GetMapping(UrlsMapping.GET_ALL_UNBILLED)
    public List<UnbilledDTO> getAllUnbilled() {
        return unbilledService.getAllUnbilled();
    }
    
    @GetMapping(UrlsMapping.GET_ALL_UNBILLED)
    public List<Map<String,Object>> getAllUnbilledWithPagination(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
        return unbilledService.getAllUnbilled(page-1,size);
    }

    @GetMapping(UrlsMapping.GET_UNBILLED_BY_ID)
    public ResponseEntity<UnbilledDTO> getUnbilledById(@PathVariable Long id) {
        UnbilledDTO dto = unbilledService.getUnbilledById(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping(UrlsMapping.CREATE_UNBILLED)
    public ResponseEntity<UnbilledDTO> createUnbilled(@RequestBody UnbilledDTO dto) {
        UnbilledDTO created = unbilledService.createUnbilled(dto);
        return ResponseEntity.ok(created);
    }
    
    
    @GetMapping(UrlsMapping.GET_ALL_UNBILLED_AMOUNT)
    public Map<String,Object> getAllUnbilledAmount() {
        return unbilledService.getAllUnbilledAmount();
    }

    @GetMapping(UrlsMapping.GET_ALL_UNBILLED_COUNT)
    public int getAllUnbilledCount() {
        return unbilledService.getAllUnbilledCount();
    }
  
    @GetMapping(UrlsMapping.SEARCH_UNBILLED)
    public List<Map<String,Object>> searchUnbilled(String name,String searchBy) {
        return unbilledService.searchUnbilled(name,searchBy);
    }
    
    @GetMapping(UrlsMapping.GET_ALL_UNBILLED_FOR_EXPORT)
    public List<Map<String,Object>> getAllUnbilledForExport(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate) {
        return unbilledService.getAllUnbilledForExport(startDate,endDate);
    }
    
    @GetMapping(UrlsMapping.GET_UNBILLED_BY_ID_FOR_VIEW)
    public Map<String,Object> getUnbilledByIdForView(@RequestParam Long id) {
        return unbilledService.getUnbilledByIdForView(id);
    }
    
    
}