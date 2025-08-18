package com.account.dashboard.controller.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.account.dashboard.dto.UnbilledDTO;
import com.account.dashboard.service.UnbilledService;
import com.account.dashboard.util.UrlsMapping;

import java.util.List;

@RestController
public class UnbilledController {

    @Autowired
    private UnbilledService unbilledService;

    @GetMapping(UrlsMapping.GET_ALL_UNBILLED)
    public List<UnbilledDTO> getAllUnbilled() {
        return unbilledService.getAllUnbilled();
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

  
}