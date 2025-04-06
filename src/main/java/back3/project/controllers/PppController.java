package back3.project.controllers;

import back3.project.dto.PppListDto;
import back3.project.service.PppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://194.87.56.253:3000")
@RequestMapping("/api/ppp")
@RequiredArgsConstructor
public class PppController {

    private final PppService pppService;

    @GetMapping
    public ResponseEntity<PppListDto> getAllPpps() {
        try {
            PppListDto pppListDto = pppService.getAllPpps();
            return ResponseEntity.ok(pppListDto);
        } catch (Exception e) {
            // Логируем ошибку
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}