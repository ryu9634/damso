package com.damso.match.api.admin.venue;

import com.damso.match.api.admin.venue.dto.DrinkItemDto;
import com.damso.match.domain.venue.service.DrinkService;
import com.damso.match.global.common.FileStorageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 관리자 — 앱 홈에 보여줄 술 · 칵테일 메뉴 관리. */
@RestController
@RequestMapping("/api/admin/drinks")
@RequiredArgsConstructor
public class AdminDrinkController {

    private final DrinkService drinkService;
    private final FileStorageService fileStorage;

    @GetMapping
    public List<DrinkItemDto> list() {
        return drinkService.listAll();
    }

    @PostMapping
    public DrinkItemDto create(@Valid @RequestBody DrinkItemDto.SaveRequest req) {
        return drinkService.create(req);
    }

    @PutMapping("/{id}")
    public DrinkItemDto update(@PathVariable Long id, @Valid @RequestBody DrinkItemDto.SaveRequest req) {
        return drinkService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        drinkService.delete(id);
    }

    /** 위/아래 이동 — 화면에 보이는 순서대로 id 를 보낸다. */
    @PutMapping("/order")
    public List<DrinkItemDto> reorder(@RequestBody List<Long> idsInOrder) {
        drinkService.reorder(idsInOrder);
        return drinkService.listAll();
    }

    /** 메뉴 사진 업로드 — 저장된 경로를 돌려준다. */
    @PostMapping("/image")
    public Map<String, String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Map.of("url", fileStorage.storeImage(file));
    }
}
