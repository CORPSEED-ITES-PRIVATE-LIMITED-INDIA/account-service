package com.account.dashboard.controller.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.account.dashboard.dto.UnbilledDTO;
import com.account.dashboard.service.UnbilledService;
import com.account.dashboard.util.UrlsMapping;

import java.util.List;

@RestController
@RequestMapping("/api/unbilled")
public class UnbilledController {

    @Autowired
    private UnbilledService unbilledService;

    @GetMapping(UrlsMapping.GET_ALL_UNBILLED)
    public List<UnbilledDTO> getAll() {
        return unbilledService.getAll();
    }

    @GetMapping(UrlsMapping.GET_UNBILLED_BY_ID)
    public ResponseEntity<UnbilledDTO> getById(@PathVariable Long id) {
        UnbilledDTO dto = unbilledService.getById(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping(UrlsMapping.CREATE_UNBILLED)
    public ResponseEntity<UnbilledDTO> create(@RequestBody UnbilledDTO dto) {
        UnbilledDTO created = unbilledService.create(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnbilledDTO> update(@PathVariable Long id, @RequestBody UnbilledDTO dto) {
        UnbilledDTO updated = unbilledService.update(id, dto);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        unbilledService.delete(id);
        return ResponseEntity.noContent().build();
    }
}