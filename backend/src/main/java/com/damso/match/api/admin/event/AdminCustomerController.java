package com.damso.match.api.admin.event;

import com.damso.match.api.admin.event.dto.CustomerUpdateRequest;
import com.damso.match.domain.event.dto.CustomerCreateRequest;
import com.damso.match.domain.event.dto.CustomerDto;
import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.CustomerStatus;
import com.damso.match.domain.event.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;

    @GetMapping
    public List<CustomerDto> list(@RequestParam Long eventId) {
        return customerService.listAll(eventId).stream().map(CustomerDto::from).toList();
    }

    @PostMapping
    public CustomerDto create(@RequestBody @Valid CustomerCreateRequest req) {
        Customer c = customerService.create(req);
        return CustomerDto.from(c);
    }

    @PutMapping("/{id}")
    public CustomerDto update(@PathVariable Long id, @RequestBody @Valid CustomerUpdateRequest req) {
        return CustomerDto.from(customerService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    public CustomerDto setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        CustomerStatus s = CustomerStatus.valueOf(body.get("status"));
        return CustomerDto.from(customerService.setStatus(id, s));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
